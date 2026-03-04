package com.messagerie.dao;

import com.messagerie.model.User;
import com.messagerie.model.UserStatus;
import com.messagerie.utils.PasswordUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.hibernate.exception.ConstraintViolationException;

import java.util.List;

public class UserDAO {

    public void save(User user) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            session.save(user);

            transaction.commit();

            System.out.println("Utilisateur sauvegardé avec succès: " + user.getUsername());

        } catch (ConstraintViolationException e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur de contrainte d'unicité: " + user.getUsername());
            throw new RuntimeException("Ce nom d'utilisateur existe déjà", e);

        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors de la sauvegarde: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de la création de l'utilisateur", e);
        }
    }

    public User findByUsername(String username) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            Query<User> query = session.createQuery(
                    "FROM User WHERE username = :username",
                    User.class
            );

            query.setParameter("username", username);

            return query.uniqueResult();

        } catch (Exception e) {
            System.err.println("Erreur lors de la recherche: " + e.getMessage());
            return null;
        }
    }

    public List<User> getAllUsers() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            return session.createQuery("FROM User ORDER BY username", User.class)
                    .list();

        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération: " + e.getMessage());
            return List.of();
        }
    }


    public List<User> getOnlineUsers() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            Query<User> query = session.createQuery(
                    "FROM User WHERE status = :status ORDER BY username",
                    User.class
            );
            query.setParameter("status", UserStatus.ONLINE);

            return query.list();

        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des utilisateurs en ligne: " + e.getMessage());
            return List.of();
        }
    }


    public void updateStatus(Long userId, UserStatus status) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            User user = session.get(User.class, userId);

            if (user != null) {

                user.setStatus(status);


                session.update(user);

                System.out.println("Statut mis à jour pour " + user.getUsername() + ": " + status);
            }

            transaction.commit();

        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors de la mise à jour du statut: " + e.getMessage());
        }
    }


    public boolean authenticate(String username, String password) {
        User user = findByUsername(username);

        if (user != null) {
            // Vérification du mot de passe hashé avec BCrypt (RG9)
            return PasswordUtil.checkPassword(password, user.getPassword());
        }

        return false;
    }


    public boolean register(String username, String password) {

        // RG1: Vérification d'unicité
        if (findByUsername(username) != null) {
            System.out.println("Échec d'inscription: nom déjà pris - " + username);
            return false;
        }

        // RG9: Hachage du mot de passe avec BCrypt
        String hashedPassword = PasswordUtil.hashPassword(password);

        // Création du nouvel utilisateur
        User user = new User(username, hashedPassword);

        try {
            // Sauvegarde en base
            save(user);
            System.out.println("Nouvel utilisateur enregistré: " + username);
            return true;

        } catch (Exception e) {
            System.err.println("Échec de l'enregistrement: " + e.getMessage());
            return false;
        }
    }
}
