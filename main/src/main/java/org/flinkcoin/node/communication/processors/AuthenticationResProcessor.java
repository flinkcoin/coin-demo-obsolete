package org.flinkcoin.node.communication.processors;

import org.flinkcoin.data.proto.common.Common;
import org.flinkcoin.data.proto.communication.Message;
import org.flinkcoin.helper.Pair;
import org.flinkcoin.helper.helpers.Base32Helper;
import org.flinkcoin.node.managers.CryptoManager;
import org.flinkcoin.node.managers.NodeManager;
import org.flinkcoin.node.services.NodeService;
import com.google.protobuf.Any;
import io.netty.channel.ChannelHandlerContext;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.flinkcoin.node.communication.BaseProcessor;
import org.flinkcoin.node.communication.ChannelData;
import org.flinkcoin.node.communication.CommonProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AuthenticationResProcessor extends BaseProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResProcessor.class);

    private final NodeManager nodeManager;
    private final CryptoManager cryptoManager;
    private final Provider<CommonProcessor> commonHandlerProvider;
    private final Provider<NodeService> discoveryServiceProvider;

    @Inject
    public AuthenticationResProcessor(NodeManager nodeManager, CryptoManager cryptoManager, Provider<CommonProcessor> commonHandlerProvider, Provider<NodeService> discoveryServiceProvider) {
        this.nodeManager = nodeManager;
        this.cryptoManager = cryptoManager;
        this.commonHandlerProvider = commonHandlerProvider;
        this.discoveryServiceProvider = discoveryServiceProvider;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, ChannelData channelData, Message req) throws Exception {

        Any any = req.getAny();
        Message.AuthenticationRes authenticationRes = any.unpack(Message.AuthenticationRes.class);
        Message.AuthenticationRes.Body body = authenticationRes.getBody();
        Common.Node node = body.getNode();
        Common.NodeAddress nodeAddress = body.getNodeAddress();

        LOGGER.debug("Node {} sending auth res!", Base32Helper.encode(node.getNodeId().toByteArray()));

        if (!Arrays.equals(channelData.getToken(), body.getToken().toByteArray())) {
            LOGGER.debug("Node {} not sending correct token!", node.getNodeId());
            eroorCloseChannel(ctx);
            return;
        }
        if (!nodeManager.checkNodeExists(node.getNodeId())) {
            LOGGER.debug("Node {} not in DB!", Base32Helper.encode(node.getNodeId().toByteArray()));
            discoveryServiceProvider.get().newNode(Pair.of(node, nodeAddress));
            eroorCloseChannel(ctx);
            return;

        }

        if (!cryptoManager.verifyData(node.getNodeId(), body.toByteString(), authenticationRes.getSignature())) {
            LOGGER.debug("Node {} sending wrong singature!", Base32Helper.encode(node.getNodeId().toByteArray()));
            eroorCloseChannel(ctx);
            return;
        }
        LOGGER.debug("Node {} authentication succeess!", Base32Helper.encode(node.getNodeId().toByteArray()));
        channelData.setNodeId(node.getNodeId());
        channelData.authenticate();

        CommonProcessor commonHandler = commonHandlerProvider.get();

        if (!commonHandler.addNode(node.getNodeId(), ctx.channel().id())) {
            LOGGER.info("Duplicate channell detected for nodeId: {}", node.getNodeId());
            ctx.close();
        }

    }

}
