package com.messagerie.model;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Classe représentant un message échangé entre deux utilisateurs
 *
 * @author MessagerieApp
 */

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-incrémentation PostgreSQL
    private Long id;

    @ManyToOne // Relation Many-to-One: plusieurs messages peuvent être envoyés par un utilisateur
    @JoinColumn(name = "sender_id", nullable = false)
    // sender_id sera une clé étrangère vers la table users
    private User sender; // Expéditeur du message

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver; // Destinataire du message

    @Column(nullable = false, length = 1000)
    // length = 1000 -> RG7: Le message ne doit pas dépasser 1000 caractères
    private String contenu; // Contenu textuel du message

    @Column(name = "date_envoi")
    private LocalDateTime dateEnvoi; // Date et heure d'envoi

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MessageStatus statut; // ENVOYE, RECU, LU

    /**
     * Constructeur par défaut JPA
     */
    public Message() {}

    /**
     * Constructeur pour créer un nouveau message
     * @param sender Expéditeur
     * @param receiver Destinataire
     * @param contenu Contenu du message
     */
    public Message(User sender, User receiver, String contenu) {
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
        this.dateEnvoi = LocalDateTime.now(); // Horodatage automatique
        this.statut = MessageStatus.ENVOYE; // Statut initial: ENVOYE
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getReceiver() { return receiver; }
    public void setReceiver(User receiver) { this.receiver = receiver; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }

    public MessageStatus getStatut() { return statut; }
    public void setStatut(MessageStatus statut) { this.statut = statut; }
}