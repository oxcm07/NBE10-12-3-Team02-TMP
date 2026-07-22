package com.back.domain.concert.service;

import com.back.domain.concert.dto.ConcertDetailResponse;
import com.back.domain.concert.dto.ConcertListResponse;
import com.back.domain.concert.entity.Concert;
import com.back.domain.concert.entity.ConcertDetail;
import com.back.domain.concert.enums.ConcertSortType;
import com.back.domain.concert.repository.ConcertDeatilRepository;
import com.back.domain.concert.repository.ConcertRepository;
import com.back.domain.schedule.entity.Schedule;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.entity.SeatStatus;
import com.back.domain.schedule.repository.ScheduleRepository;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {
    private final ScheduleSeatRepository scheduleSeatRepository;
    private final ScheduleRepository scheduleRepository;
    private final ConcertRepository concertRepository;
    private final ConcertDeatilRepository concertDeatilRepository;

    public List<ConcertListResponse> getConcerts(String keyword, ConcertSortType sort, LocalDate date) {
        List<Concert> concerts = concertRepository.findByKeyword(keyword);

        List<Long> concertIds = concerts.stream()
                .map(Concert::getConcertId)
                .toList();

        List<Schedule> schedules = scheduleRepository.findAllWithVenueByConcertIds(concertIds);

        if (date != null) {
            Set<Long> concertIdsWithMatchingSchedule = schedules.stream()
                    .filter(schedule -> schedule.getScheduleDate().toLocalDate().equals(date))
                    .map(schedule -> schedule.getConcert().getConcertId())
                    .collect(Collectors.toSet());

            concerts = concerts.stream()
                    .filter(concert -> concertIdsWithMatchingSchedule.contains(concert.getConcertId()))
                    .toList();
        }

        Map<Long, String> venueNameMap = schedules.stream()
                .collect(Collectors.toMap(
                        schedule -> schedule.getConcert().getConcertId(),
                        schedule -> schedule.getVenue().getVenueName(),
                        (existing, replacement) -> existing
                ));

        Comparator<Concert> comparator;
        if (sort == ConcertSortType.latest) {
            comparator = Comparator.comparing(Concert::getStartDate).reversed();
        } else {
            comparator = Comparator.comparing(Concert::getEndDate);
        }

        return concerts.stream()
                .sorted(comparator)
                .map(concert -> {
                    String venueName = venueNameMap.getOrDefault(concert.getConcertId(), "");
                    return ConcertListResponse.of(concert, venueName);
                })
                .collect(Collectors.toList());
    }

    public ConcertDetailResponse getConcertDetail(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CONCERT_NOT_FOUND));

        Schedule schedule = scheduleRepository.findWithVenueByConcertId(concertId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ServiceException(ErrorCode.CONCERT_SCHEDULE_EMPTY));

        List<String> detailUrlList = concertDeatilRepository
                .findByConcertConcertId(concertId)
                .stream()
                .map(ConcertDetail::getUrlDetail)
                .collect(Collectors.toList());

        List<ScheduleSeat> scheduleSeats = scheduleSeatRepository.findByScheduleScheduleId(schedule.getScheduleId());
        Map<String, Integer> prices = convertToPriceMap(scheduleSeats);

        boolean bookable = concert.getEndDate().isAfter(LocalDateTime.now());

        return ConcertDetailResponse.of(
                concert,
                schedule.getVenue().getVenueName(),
                schedule.getVenue().getLocation(),
                detailUrlList,
                prices,
                bookable
        );
    }

    public List<ScheduleSeat> getScheduleSeats(Long scheduleId) {
        return scheduleSeatRepository.findByScheduleScheduleId(scheduleId);
    }

    @Transactional
    public void validateSeatAvailable(Long scheduleId, String seatNumber) {
        ScheduleSeat seat = scheduleSeatRepository
                .findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber)
                .orElseThrow(() -> new ServiceException(ErrorCode.SEAT_NOT_FOUND));

        if (seat.getSeatStatus() == SeatStatus.SOLD_OUT) {
            throw new ServiceException(ErrorCode.SEAT_ALREADY_SOLD);
        }
        if (seat.getSeatStatus() == SeatStatus.HOLD) {
            throw new ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER);
        }
    }


    public void validateConcertScheduleMatch(Long concertId, Long scheduleId) {
        scheduleRepository.findByScheduleIdAndConcert_ConcertId(scheduleId, concertId)
                .orElseThrow(() -> new ServiceException(ErrorCode.INVALID_CONCERT_SCHEDULE));
    }

    public Map<String, Integer> convertToPriceMap(List<ScheduleSeat> scheduleSeats) {
        return scheduleSeats.stream()
                .collect(Collectors.toMap(
                        ScheduleSeat::getGradeName,
                        ScheduleSeat::getSeatPrice,
                        (oldPrice, newPrice) -> oldPrice
                ));
    }

    public void validateScheduleBookable(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CONCERT_SCHEDULE_EMPTY));

        if (LocalDateTime.now().isAfter(schedule.getScheduleDate())) {
            throw new ServiceException(ErrorCode.EXPIRED_BOOKING_DEADLINE);
        }
    }
}
