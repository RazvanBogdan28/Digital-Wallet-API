package com.razvan.digital_wallet_api.entity;

import jakarta.persistence.*;


import java.math.BigDecimal;

@Entity
@Table(
        name = "wallets",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "currency"})
        }
)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    public Wallet() {
    }

    public Wallet(Currency currency, BigDecimal balance, User user) {
        this.currency = currency;
        this.balance = balance;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Long getVersion() {
        return version;
    }
}
