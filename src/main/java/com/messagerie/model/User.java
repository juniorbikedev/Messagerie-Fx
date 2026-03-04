package com.messagerie.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Classe représentant un utilisateur de l'application de messagerie
 * Mappée avec JPA pour la persistance en base de données PostgreSQL
 *
 * @author MessagerieApp
 * @version 1.0
 */
@Entity // Indique que cette classe est une entité JPA (sera mappée à une table)
@Table(name = "users") // Spécifie le nom de la table dans PostgreSQL
public class User {

    @Id // Marque ce champ comme clé primaire
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-incrémentation PostgreSQL (SERIAL)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    // unique = true -> RG1: Le username doit être unique
    // nullable = false -> Le username est obligatoire
    private String username;

    @Column(nullable = false) // Le mot de passe est obligatoire
    private String password; // Sera stocké hashé (RG9)

    @Enumerated(EnumType.STRING) // Stocke l'enum sous forme de texte dans PostgreSQL
    @Column(length = 20)
    private UserStatus status; // ONLINE ou OFFLINE (RG4)

    @Column(name = "date_creation") // Nom de la colonne dans PostgreSQL
    private LocalDateTime dateCreation; // Date d'inscription

    // Relations OneToMany pour les messages envoyés et reçus
    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Message> sentMessages; // Messages envoyés par cet utilisateur

    @OneToMany(mappedBy = "receiver", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Message> receivedMessages; // Messages reçus par cet utilisateur

    /**
     * Constructeur par défaut requis par JPA
     */
    public User() {}

    /**
     * Constructeur pour créer un nouvel utilisateur
     * @param username Nom d'utilisateur unique
     * @param password Mot de passe hashé
     */
    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.dateCreation = LocalDateTime.now(); // Date d'inscription automatique
        this.status = UserStatus.OFFLINE; // Par défaut, nouvel utilisateur est hors ligne
    }

    // Getters et Setters avec commentaires
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

    /**
     * toString adapté pour l'affichage dans l'interface
     * @return Représentation textuelle de l'utilisateur avec son statut
     */
    @Override
    public String toString() {
        return username + " (" + status + ")";
    }
}
