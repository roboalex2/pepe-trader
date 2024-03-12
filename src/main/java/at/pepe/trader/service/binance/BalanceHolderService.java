package at.pepe.trader.service.binance;

import at.pepe.trader.config.TradeConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceHolderService {

    private final TradeConfigProperties tradeConfigProperties;

    private Map<String, BigDecimal> freeAssets  = new ConcurrentHashMap<>();

    public BigDecimal getAvailableQuoteAsset() {
        BigDecimal quoteAssetAmount = freeAssets.get(tradeConfigProperties.getQuoteAsset());
        if (quoteAssetAmount == null) {
            return new BigDecimal(tradeConfigProperties.getQuoteAssetQuantityPerTrade().doubleValue() + 1); // TODO for now just assume
        }
        return quoteAssetAmount;
    }

    public void updateAssets(JSONObject jsonObject) {
        JSONArray assetsArray = jsonObject.getJSONArray("B");
        for (int i = 0; i < assetsArray.length(); i++) {
            JSONObject asset = assetsArray.getJSONObject(i);
            freeAssets.put(asset.getString("a"), asset.getBigDecimal("f"));
        }
    }

}
