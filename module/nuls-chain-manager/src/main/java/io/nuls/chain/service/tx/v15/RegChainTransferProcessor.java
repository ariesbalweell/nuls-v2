package io.nuls.chain.service.tx.v15;

import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.Transaction;
import io.nuls.base.protocol.TransactionProcessor;
import io.nuls.chain.info.CmRuntimeInfo;
import io.nuls.chain.model.dto.ChainEventResult;
import io.nuls.chain.model.po.Asset;
import io.nuls.chain.model.po.BlockChain;
import io.nuls.chain.rpc.call.RpcService;
import io.nuls.chain.service.CacheDataService;
import io.nuls.chain.service.ChainService;
import io.nuls.chain.service.CmTransferService;
import io.nuls.chain.service.ValidateService;
import io.nuls.chain.util.ChainManagerUtil;
import io.nuls.chain.util.LoggerUtil;
import io.nuls.chain.util.TxUtil;
import io.nuls.core.constant.TxType;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("RegChainTxProcessorV15")
public class RegChainTransferProcessor implements TransactionProcessor {
    @Autowired
    private ValidateService validateService;
    @Autowired
    private CacheDataService cacheDataService;
    @Autowired
    private ChainService chainService;
    @Autowired
    private RpcService rpcService;
    @Autowired
    CmTransferService cmTransferService;

    @Override
    public int getType() {
        return TxType.REGISTER_CHAIN_AND_ASSET;
    }

    @Override
    public Map<String, Object> validate(int chainId, List<Transaction> txs, Map<Integer, List<Transaction>> txMap, BlockHeader blockHeader) {
        List<Transaction> errorList = new ArrayList<>();
        Map<String, Object> rtData = new HashMap<>(2);
        rtData.put("errorCode", "");
        rtData.put("txList", errorList);
        try {
            Map<String, Integer> chainMap = new HashMap<>();
            Map<String, Integer> assetMap = new HashMap<>();
            BlockChain blockChain = null;
            Asset asset = null;
            ChainEventResult chainEventResult = ChainEventResult.getResultSuccess();
            for (Transaction tx : txs) {
                String txHash = tx.getHash().toHex();
                blockChain = TxUtil.buildChainWithTxDataV4(tx, false);
                asset = TxUtil.buildAssetWithTxChainV4(tx);
                String assetKey = CmRuntimeInfo.getAssetKey(asset.getChainId(), asset.getAssetId());
                chainEventResult = validateService.batchChainRegValidatorV15(blockChain, asset, chainMap, assetMap);
                if (chainEventResult.isSuccess()) {
                    ChainManagerUtil.putChainMap(blockChain, chainMap);
                    assetMap.put(assetKey, 1);
                    LoggerUtil.logger().debug("txHash = {},chainId={} reg batchValidate success!", txHash, blockChain.getChainId());
                } else {
                    rtData.put("errorCode", chainEventResult.getErrorCode().getCode());
                    LoggerUtil.logger().error("txHash = {},chainId={},magicNumber={} reg batchValidate fail!", txHash, blockChain.getChainId(), blockChain.getMagicNumber());
                    errorList.add(tx);
                }
            }
        } catch (Exception e) {
            LoggerUtil.logger().error(e);
            throw new RuntimeException(e);
        }
        return rtData;
    }

    @Override
    public boolean commit(int chainId, List<Transaction> txs, BlockHeader blockHeader) {
        LoggerUtil.logger().debug("reg chain tx count = {}", txs.size());
        long commitHeight = blockHeader.getHeight();
        BlockChain blockChain = null;
        Asset asset = null;
        List<BlockChain> blockChains = new ArrayList<>();
        List<Map<String, Object>> prefixList = new ArrayList<>();
        try {
            for (Transaction tx : txs) {
                blockChain = TxUtil.buildChainWithTxDataV4(tx, false);
                asset = TxUtil.buildAssetWithTxChainV4(tx);
                BlockChain dbChain = chainService.getChain(blockChain.getChainId());
                //继承数据
                if (null != dbChain) {
                    blockChain.setSelfAssetKeyList(TxUtil.moveRepeatInfo(dbChain.getSelfAssetKeyList()));
                    blockChain.setTotalAssetKeyList(TxUtil.moveRepeatInfo(dbChain.getTotalAssetKeyList()));
                } else {
                    blockChain.addCreateAssetId(CmRuntimeInfo.getAssetKey(blockChain.getChainId(), asset.getAssetId()));
                    blockChain.addCirculateAssetId(CmRuntimeInfo.getAssetKey(blockChain.getChainId(), asset.getAssetId()));
                }

                chainService.registerBlockChain(blockChain, asset);
                blockChains.add(blockChain);
                Map<String, Object> prefix = new HashMap<>(2);
                prefix.put("chainId", blockChain.getChainId());
                prefix.put("addressPrefix", blockChain.getAddressPrefix());
                prefixList.add(prefix);
            }
        } catch (Exception e) {
            LoggerUtil.logger().error(e);
            //通知远程调用回滚
            try {
                chainService.rpcBlockChainRollback(txs, blockHeader.getTime());
                //进行回滚
                cacheDataService.rollBlockTxs(chainId, commitHeight);
            } catch (Exception e1) {
                LoggerUtil.logger().error(e);
                throw new RuntimeException(e);
            }
            return false;
        }
        rpcService.registerCrossChain(blockChains);
        rpcService.addAcAddressPrefix(prefixList);
        return true;
    }

    @Override
    public boolean rollback(int chainId, List<Transaction> txs, BlockHeader blockHeader) {
        try {
            return cmTransferService.rollbackV3(chainId, txs, blockHeader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
