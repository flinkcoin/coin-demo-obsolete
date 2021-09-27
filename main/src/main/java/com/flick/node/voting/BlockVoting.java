package com.flick.node.voting;

import com.flick.data.proto.api.Api.InfoRes;
import com.flick.data.proto.api.Api.InfoRes.BlockConfirm;
import com.flick.helper.Pair;
import com.flick.node.api.AccountServiceImpl;
import com.flick.node.configuration.ProcessorBase;
import com.flick.node.services.BlockVotingService;
import com.flick.node.storage.Storage;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BlockVoting extends ProcessorBase<Pair<ByteString, ByteString>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockVoting.class);

    private static final int MAX_ELEMENTS = 10000;
    private static final int EXPIRY_TIME = 6;

    private static final long WEIGHT_TRESHOLD = 90;

    private final Cache<ByteString, BlockVotes> cache;
    private final Storage storage;
    private final BlockVotingService blockVotingService;

    @Inject
    public BlockVoting(Storage storage, BlockVotingService blockVotingService) {
        super(PublishProcessor.create());
        this.blockVotingService = blockVotingService;
        this.storage = storage;

        this.publishProcessor
                .onBackpressureBuffer(1000, () -> {
                }, BackpressureOverflowStrategy.DROP_LATEST)
                .observeOn(Schedulers.single())
                .subscribe(this);

        this.cache = new Cache2kBuilder<ByteString, BlockVotes>() {
        }
                .name("BlockVoting")
                .eternal(false)
                .sharpExpiry(true)
                .entryCapacity(MAX_ELEMENTS)
                .expireAfterWrite(EXPIRY_TIME, TimeUnit.SECONDS)
                .build();
    }

    public void newBlockVote(Pair<ByteString, ByteString> pair) {
        publishProcessor.onNext(pair);
    }

    @Override
    public void process(Pair<ByteString, ByteString> pair) {

        ByteString nodeId = pair.getFirst();
        ByteString blockHash = pair.getSecond();

        BlockVotes blockVotes = cache.get(blockHash);

        if (blockVotes == null) {
            blockVotes = new BlockVotes();
            cache.put(blockHash, blockVotes);
        }

        boolean success = blockVotes.addVote(nodeId, new BlockVote(99));

        if (!success) {
            return;
        }
        blockVotingService.newBlock(blockHash);
    }

    public static class BlockVotes {

        private final Map<ByteString, BlockVote> voteByNode;
        private long sumWeight;

        public BlockVotes() {
            this.sumWeight = 0;
            this.voteByNode = new HashMap<>();
        }

        public boolean addVote(ByteString nodeId, BlockVote blockVote) {

            if (voteByNode.containsKey(nodeId)) {
                LOGGER.debug("Node {} trying to vote multiple times!", nodeId);
                return false;
            }
            voteByNode.put(nodeId, blockVote);
            sumWeight += blockVote.weight;

            return sumWeight > WEIGHT_TRESHOLD;
        }

    }

    public static class BlockVote {

        private final long weight;

        public BlockVote(long weight) {
            this.weight = weight;
        }
    }
}
