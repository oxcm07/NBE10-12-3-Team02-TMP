package com.back.domain.ticket.entity;

import com.back.domain.schedule.entity.Schedule;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.user.entity.User;
import com.back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_seat_id", nullable = false)
    private ScheduleSeat scheduleSeat;

    @Column(nullable = false, unique = true)
    private String ticketNumber;

    @Column(unique = true)
    private String qrToken;

    @Column(nullable = false)
    private int ticketPrice;

    @Column(nullable = false)
    private boolean isValid = true;

   private Ticket(User user,Schedule schedule,ScheduleSeat scheduleSeat,String ticketNumber,String qrToken,int ticketPrice,boolean isValid) {
       this.user = user;
       this.schedule = schedule;
       this.scheduleSeat = scheduleSeat;
       this.ticketNumber = ticketNumber;
       this.qrToken = qrToken;
       this.ticketPrice = ticketPrice;
       this.isValid = isValid;
   }
   public static Ticket create(User user,Schedule schedule,ScheduleSeat scheduleSeat,String ticketNumber,int ticketPrice) {
       return new Ticket(user,schedule,scheduleSeat,ticketNumber, UUID.randomUUID().toString(),ticketPrice,true);
   }
    public void updateIsValid(boolean isValid) {
        this.isValid = isValid;
    }
}
