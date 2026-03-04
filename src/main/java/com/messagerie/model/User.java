package com.messagerie.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    // Relations OneToMany pour les messages envoyés et reçus
    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Message> sentMessages; // Messages envoyés par cet utilisateur

    @OneToMany(mappedBy = "receiver", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Message> receivedMessages;

    public User() {}


    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.dateCreation = LocalDateTime.now();
        this.status = UserStatus.OFFLINE;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }


    @Override
    public String toString() {
        return username + " (" + status + ")";
    }
}
