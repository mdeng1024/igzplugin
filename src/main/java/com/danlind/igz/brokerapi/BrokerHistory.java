package com.danlind.igz.brokerapi;

import com.danlind.igz.adapter.RestApiAdapter;
import com.danlind.igz.config.ZorroReturnValues;
import com.danlind.igz.domain.types.Epic;
import com.danlind.igz.domain.types.Resolution;
import com.danlind.igz.ig.api.client.rest.dto.prices.getPricesV3.GetPricesV3Response;
import com.danlind.igz.ig.api.client.rest.dto.prices.getPricesV3.PricesItem;
import com.danlind.igz.time.TimeConvert;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.danlind.igz.domain.types.Resolution.*;

public class BrokerHistory {

    private final static Logger logger = LoggerFactory.getLogger(HistoryHandler.class);
private static final String PAGE_NUMBER = "1";
    private final RestApiAdapter restApiAdapter;

    @Autowired
    public BrokerHistory(RestApiAdapter restApiAdapter) {
        this.restApiAdapter = restApiAdapter;
    }

    //TODO: Check why not working
    public int getPriceHistory(final Epic epic,
                               final double tStart,
                               final double tEnd,
                               final int nTickMinutes,
                               final int nTicks,
                               final double tickParams[]) {

        logger.debug("Finding resolution {}", nTickMinutes);
        Resolution resolution = checkValidResolution(nTickMinutes);
        if (resolution != Resolution.INVALID) {
            return fillParamsForValidResolution(epic, tStart, tEnd, nTicks, tickParams, resolution);
        } else {
            logger.info("Unable to find valid resolution for {}", resolution.name());
            return ZorroReturnValues.HISTORY_UNAVAILABLE.getValue();
        }
    }

    private int fillParamsForValidResolution(Epic epic, double tStart, double tEnd, int nTicks, double[] tickParams, Resolution resolution) {
        int accountZoneOffset = restApiAdapter.getTimeZoneOffset();
        LocalDateTime endDateTime = TimeConvert.localDateTimeFromOLEDate(tEnd, accountZoneOffset);
        LocalDateTime startDateTime = TimeConvert.localDateTimeFromOLEDate(tStart, accountZoneOffset);
        logger.debug("Getting prices for {} - {}, max ticks {}, resolution {}", startDateTime, endDateTime, nTicks, resolution.name());
        GetPricesV3Response response = restApiAdapter.getHistoricPrices(PAGE_NUMBER,
            String.valueOf(nTicks),
            String.valueOf(nTicks),
            epic.getName(),
            startDateTime.toString(),
            endDateTime.toString(),
            resolution.name());

        //If no data was found for the specified date range, fallback and get last nTicks
        if (response.getPrices().size() == 0) {
            logger.warn("Zero ticks returned for requested date range {} - {}", startDateTime, endDateTime);
            return ZorroReturnValues.HISTORY_UNAVAILABLE.getValue();
        }

        int tickParamsIndex = 0;
        for (int i = 0; i < response.getPrices().size(); ++i) {
            final PricesItem priceItem = response.getPrices().get(i);
            tickParams[tickParamsIndex] = priceItem.getOpenPrice().getAsk().doubleValue();
            tickParams[tickParamsIndex + 1] = priceItem.getClosePrice().getAsk().doubleValue();
            tickParams[tickParamsIndex + 2] = priceItem.getHighPrice().getAsk().doubleValue();
            tickParams[tickParamsIndex + 3] = priceItem.getLowPrice().getAsk().doubleValue();
            tickParams[tickParamsIndex + 4] = TimeConvert.getOLEDateFromMillisRounded(LocalDateTime.parse(priceItem.getSnapshotTimeUTC()).toInstant(ZoneOffset.UTC).toEpochMilli());
            tickParams[tickParamsIndex + 5] = priceItem.getClosePrice().getAsk().subtract(priceItem.getClosePrice().getBid()).doubleValue();
            tickParams[tickParamsIndex + 6] = priceItem.getLastTradedVolume().doubleValue();
            tickParamsIndex += 7;
        }

        return response.getPrices().size();
    }

    public List<PricesItem> getPriceHistory(final Epic epic,
                                            final int ticks) {
        return restApiAdapter.getHistoricPrices(PAGE_NUMBER,
            Integer.toString(ticks),
            Integer.toString(ticks),
            epic.getName(),
            null,
            null,
            MINUTE.name()).getPrices();
    }

    //    DAY	1 day
//    HOUR	1 hour
//    HOUR_2	2 hours
//    HOUR_3	3 hours
//    HOUR_4	4 hours
//    MINUTE	1 minute
//    MINUTE_10	10 minutes
//    MINUTE_15	15 minutes
//    MINUTE_2	2 minutes
//    MINUTE_3	3 minutes
//    MINUTE_30	30 minutes
//    MINUTE_5	5 minutes
//    MONTH	1 month
//    SECOND	1 second
//    WEEK	1 week
    private Resolution checkValidResolution(int nTickMinutes) {
        switch (nTickMinutes) {
            case 1:
                return MINUTE;
            case 2:
                return MINUTE_2;
            case 3:
                return MINUTE_3;
            case 5:
                return MINUTE_5;
            case 10:
                return MINUTE_10;
            case 15:
                return MINUTE_15;
            case 30:
                return MINUTE_30;
            case 60:
                return HOUR;
            case 120:
                return HOUR_2;
            case 180:
                return HOUR_3;
            case 240:
                return HOUR_4;
            case 1440:
                return DAY;
            default:
                return INVALID;
        }
    }
}
