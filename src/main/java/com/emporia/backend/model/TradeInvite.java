package com.emporia.backend.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trade_invites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 10)
    private String inviteCode; // e.g., EMP-8X92A

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private SMEProfile seller;

    @Column(nullable = false)
    private boolean isUsed;

    @ManyToOne
    @JoinColumn(name = "trade_record_id")
    private TradeRecord tradeRecord;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        expiresAt = LocalDateTime.now().plusDays(2);
    }
}