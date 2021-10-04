package com.flick.node.communication.processors;

import com.flick.node.handlers.IdHandler;
import org.flinkcoin.data.proto.common.Common;
import com.flick.node.communication.*;
import org.flinkcoin.data.proto.communication.Message;
import org.flinkcoin.data.proto.communication.Message.BlockPub;
import org.flinkcoin.helper.Pair;
import org.flinkcoin.helper.helpers.DateHelper;
import com.flick.node.managers.CryptoManager;
import com.flick.node.services.BlockService;
import com.flick.node.services.FloodService;
import com.google.protobuf.Any;
import io.netty.channel.ChannelHandlerContext;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BlockPubProcessor extends BaseProcessor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockPubProcessor.class);
    
    private final CryptoManager cryptoManager;
    private final BlockService blockService;
    private final FloodService messageService;
    private final IdHandler idHandler;
    
    @Inject
    public BlockPubProcessor(CryptoManager cryptoManager, BlockService blockService, FloodService messageService, IdHandler idHandler) {
        this.cryptoManager = cryptoManager;
        this.blockService = blockService;
        this.messageService = messageService;
        this.idHandler = idHandler;
    }
    
    @Override
    public void handle(ChannelHandlerContext ctx, ChannelData channelData, Message req) throws Exception {
        Any any = req.getAny();
        BlockPub blockPub = any.unpack(BlockPub.class);
        
        if (idHandler.checkExists(blockPub.getBody().getMsgId())) {
            return;
        } else {
            idHandler.putId(blockPub.getBody().getMsgId(), DateHelper.dateNow().getTime());
        }
        
        BlockPub.Body body = blockPub.getBody();
        
        if (!cryptoManager.verifyData(body.getNodeId(), body.toByteString(), blockPub.getSignature())) {
            LOGGER.info("Wrong signtaure!");
            return;
        }
        
        Common.Block block = body.getBlock();
        
        blockService.newBlock(Pair.of(channelData.getNodeId(), block));
        messageService.newMessage(req);
    }
}
