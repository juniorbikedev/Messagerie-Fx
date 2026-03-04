package com.messagerie.dao;

import com.messagerie.model.Message;
import com.messagerie.model.MessageStatus;
import com.messagerie.model.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

public class MessageDAO {

    public void save(Message message) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            session.save(message);

            transaction.commit();
            System.out.println("Message sauvegardé: " + message.getId());

        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors de la sauvegarde du message: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public List<Message> getConversation(User user1, User user2) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

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

    public List<Message> getPendingMessages(User receiver) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            // Sélectionne les messages avec statut ENVOYE (non encore reçus)
            Query<Message> query = session.createQuery(
                    "FROM Message WHERE receiver = :receiver AND statut = :status " +
                            "ORDER BY dateEnvoi ASC",
                    Message.class);

            query.setParameter("receiver", receiver);
            query.setParameter("status", MessageStatus.ENVOYE);

            return query.list();

        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des messages en attente: " + e.getMessage());
            return List.of();
        }
    }

    public void updateMessageStatus(Long messageId, MessageStatus status) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            Message message = session.get(Message.class, messageId);

            if (message != null) {
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

    public void markConversationAsRead(User receiver, User sender) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            Query<?> query = session.createQuery(
                    "UPDATE Message SET statut = :newStatus " +
                            "WHERE receiver = :receiver AND sender = :sender AND statut != :newStatus");

            query.setParameter("newStatus", MessageStatus.LU);
            query.setParameter("receiver", receiver);
            query.setParameter("sender", sender);

            int updatedCount = query.executeUpdate();

            transaction.commit();
            System.out.println(updatedCount + " messages marqués comme lus");

        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors du marquage des messages comme lus: " + e.getMessage());
        }
    }
}