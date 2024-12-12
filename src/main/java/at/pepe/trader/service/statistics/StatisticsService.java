package at.pepe.trader.service.statistics;

import at.pepe.trader.model.Position;
import at.pepe.trader.model.PositionStatus;
import at.pepe.trader.model.StatisticResult;
import at.pepe.trader.persistent.PositionRepositoryImpl;
import at.pepe.trader.service.discord.DiscordEmbedPublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final PositionRepositoryImpl positionRepository;
    private final DiscordEmbedPublishingService discordEmbedPublishingService;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 */6 * * *")
    public void generateAndPublishStatistics() {
        List<StatisticResult> statisticResults = generateStatistics();
        statisticResults.forEach(el -> discordEmbedPublishingService.sendEmbed(
                "Stats " + el.getTimeFrame(),
                el.toString(),
                "#304ffe")
        );
        log.info("Statistics sent.");
    }

    private List<StatisticResult> generateStatistics() {
        List<Position> lastMonth = positionRepository.findAllSince(OffsetDateTime.now().minusMonths(1));
        lastMonth.sort(Comparator.comparing(Position::getCreatedAt).reversed());

        List<Position> lastWeek = lastMonth.stream()
                .takeWhile(el -> OffsetDateTime.now().minusDays(7).isBefore(el.getCreatedAt()))
                .toList();

        List<Position> lastDay = lastWeek.stream()
                .takeWhile(el -> OffsetDateTime.now().minusDays(1).isBefore(el.getCreatedAt()))
                .toList();

        List<Position> lastHour = lastDay.stream()
                .takeWhile(el -> OffsetDateTime.now().minusHours(1).isBefore(el.getCreatedAt()))
                .toList();

        return List.of(
                calculateStatistics(lastMonth).toBuilder().timeFrame("Last Month").build(),
                calculateStatistics(lastWeek).toBuilder().timeFrame("Last Week").build(),
                calculateStatistics(lastDay).toBuilder().timeFrame("Last Day").build(),
                calculateStatistics(lastHour).toBuilder().timeFrame("Last Hour").build()
        );
    }

    private StatisticResult calculateStatistics(List<Position> positions) {
        return StatisticResult.builder()
                .positionsStillOpen((int) positions.stream()
                        .filter(el -> PositionStatus.OPENED.equals(el.getStatus()) || PositionStatus.WAITING_FOR_CLOSE.equals(el.getStatus()))
                        .count()
                )
                .positionsClosed((int) positions.stream()
                        .filter(el -> PositionStatus.FINISHED.equals(el.getStatus()))
                        .count()
                )
                .positionsCancelled((int) positions.stream()
                        .filter(el -> PositionStatus.CANCELLED.equals(el.getStatus()))
                        .count()
                )
                .positionsOpened(positions.size())
                .volumenTraded(positions.stream()
                        .mapToDouble(el -> el.getOpenAtPrice().multiply(el.getQuantityOpen()).doubleValue())
                        .sum()
                )
                .profitMade(positions.stream()
                        .filter(el -> PositionStatus.FINISHED.equals(el.getStatus()))
                        .map(el -> el.getCloseAtPrice().multiply(el.getQuantityClose())
                                .subtract(el.getOpenAtPrice().multiply(el.getQuantityOpen()))
                        )
                        .reduce(BigDecimal::add)
                        .orElse(new BigDecimal("-1"))
                )
                .averageTimeToClose(Duration.ofNanos((long) positions.stream()
                        .filter(el -> PositionStatus.FINISHED.equals(el.getStatus()))
                        .map(el -> el.getClosedAt() == null ? null : Duration.between(el.getCreatedAt(), el.getClosedAt()))
                        .filter(Objects::nonNull)
                        .mapToLong(Duration::toNanos)
                        .average().orElse(-1))
                ).build();
    }
}
