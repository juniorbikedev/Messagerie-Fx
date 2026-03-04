package com.messagerie.dao;

import com.messagerie.model.Message;
import com.messagerie.model.MessageStatus;
import com.messagerie.model.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

/**
 * Data Access Object pour l'entité Message
 * Gère toutes les opérations de base de données liées aux messages
 */
public class MessageDAO {

    /**
     * Sauvegarde un message en base de données
     *
     * @param message Le message à sauvegarder
     */
    public void save(Message message) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // Sauvegarde du message
            session.save(message);

            transaction.commit();
            System.out.println("Message sauvegardé: " + message.getId());

        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors de la sauvegarde du message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Récupère la conversation entre deux utilisateurs
     * RG8: Affichage par ordre chronologique (ORDER BY dateEnvoi ASC)
     *
     * @param user1 Premier utilisateur
     * @param user2 Deuxième utilisateur
     * @return Liste des messages triés par date croissante
     */
    public List<Message> getConversation(User user1, User user2) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            // Requête HQL pour récupérer tous les messages entre user1 et user2
            // (dans les deux sens) et les trier par date (RG8)
            Query<Message> query = session.createQuery(
                    "FROM Message WHERE (sender = :user1 AND receiver = :user2) " +
                            "OR (sender = :user2 AND receiver = :user1) " +
                            "ORDER BY dateEnvoi ASC", // RG8: Ordre chronologique
                    Message.class);

            query.setParameter("user1", user1);
            query.setParameter("user2", user2);

            return query.list();

        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération de la conversation: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Récupère les messages en attente pour un utilisateur
     * RG6: Messages stockés et livrés à la prochaine connexion
     *
     * @param receiver L'utilisateur destinataire
     * @return Liste des messages non reçus (statut ENVOYE)
     */
    public List<Message> getPendingMessages(User receiver) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            // Sélectionne les messages avec statut ENVOYE (non encore reçus)
            Query<Message> query = session.createQuery(
                    "FROM Message WHERE receiver = :receiver AND statut = :status " +
                            "ORDER BY dateEnvoi ASC",
                    Message.class);

            query.setParameter("receiver", receiver);
            query.setParameter("status", MessageStatus.ENVOYE); // Messages non encore reçus

            return query.list();

        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des messages en attente: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Met à jour le statut d'un message
     * Utilisé pour marquer les messages comme RECUS ou LUS
     *
     * @param messageId L'ID du message
     * @param status Le nouveau statut
     */
    public void updateMessageStatus(Long messageId, MessageStatus status) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // Récupération du message
            Message message = session.get(Message.class, messageId);

            if (message != null) {
                // Mise à jour du statut
                message.setStatut(status);
                session.update(message);

                System.out.println("Statut du message " + messageId + " mis à jour: " + status);
            }

            transaction.commit();

        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors de la mise à jour du statut: " + e.getMessage());
        }
    }

    /**
     * Marque tous les messages d'une conversation comme lus
     * Quand un utilisateur ouvre une conversation
     *
     * @param receiver L'utilisateur qui a reçu les messages
     * @param sender L'expéditeur des messages
     */
    public void markConversationAsRead(User receiver, User sender) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // Requête de mise à jour en masse
            Query<?> query = session.createQuery(
                    "UPDATE Message SET statut = :newStatus " +
                            "WHERE receiver = :receiver AND sender = :sender AND statut != :newStatus");

            query.setParameter("newStatus", MessageStatus.LU);
            query.setParameter("receiver", receiver);
            query.setParameter("sender", sender);

            int updatedCount = query.executeUpdate(); // Nombre de messages mis à jour

            transaction.commit();
            System.out.println(updatedCount + " messages marqués comme lus");

        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors du marquage des messages comme lus: " + e.getMessage());
        }
    }
}