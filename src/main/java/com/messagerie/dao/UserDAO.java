package com.messagerie.dao;

import com.messagerie.model.User;
import com.messagerie.model.UserStatus;
import com.messagerie.utils.PasswordUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.hibernate.exception.ConstraintViolationException;

import java.util.List;

/**
 * Data Access Object pour l'entité User
 * Gère toutes les opérations de base de données liées aux utilisateurs
 */
public class UserDAO {

    /**
     * Sauvegarde un utilisateur en base de données PostgreSQL
     * Utilise une transaction pour garantir l'intégrité des données
     *
     * @param user L'utilisateur à sauvegarder
     * @throws RuntimeException si le username existe déjà (RG1)
     */
    public void save(User user) {
        Transaction transaction = null;

        // Try-with-resources pour fermer automatiquement la session
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            // Début de la transaction PostgreSQL
            transaction = session.beginTransaction();

            // Sauvegarde de l'entité (INSERT en SQL)
            session.save(user);

            // Validation de la transaction (COMMIT)
            transaction.commit();

            System.out.println("Utilisateur sauvegardé avec succès: " + user.getUsername());

        } catch (ConstraintViolationException e) {
            // Gestion spécifique de la contrainte d'unicité (RG1)
            if (transaction != null) transaction.rollback(); // ROLLBACK en cas d'erreur
            System.err.println("Erreur de contrainte d'unicité: " + user.getUsername());
            throw new RuntimeException("Ce nom d'utilisateur existe déjà", e);

        } catch (Exception e) {
            // Autres erreurs (connexion PostgreSQL, etc.)
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors de la sauvegarde: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de la création de l'utilisateur", e);
        }
    }

    /**
     * Recherche un utilisateur par son nom d'utilisateur
     * Utilise HQL (Hibernate Query Language) pour la requête
     *
     * @param username Le nom d'utilisateur recherché
     * @return L'utilisateur trouvé ou null
     */
    public User findByUsername(String username) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            // Requête HQL paramétrée (protège contre les injections SQL)
            Query<User> query = session.createQuery(
                    "FROM User WHERE username = :username",
                    User.class
            );

            // Paramétrage de la requête
            query.setParameter("username", username);

            // Récupération du résultat unique (ou null)
            return query.uniqueResult();

        } catch (Exception e) {
            System.err.println("Erreur lors de la recherche: " + e.getMessage());
            return null;
        }
    }

    /**
     * Récupère tous les utilisateurs triés par nom
     * Pour l'affichage dans l'interface
     *
     * @return Liste de tous les utilisateurs
     */
    public List<User> getAllUsers() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            // Tri par nom d'utilisateur pour un affichage ordonné
            return session.createQuery("FROM User ORDER BY username", User.class)
                    .list();

        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération: " + e.getMessage());
            return List.of(); // Retourne une liste vide en cas d'erreur
        }
    }

    /**
     * Récupère uniquement les utilisateurs en ligne
     * Utilisé pour la liste des contacts connectés
     *
     * @return Liste des utilisateurs avec statut ONLINE
     */
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

    /**
     * Met à jour le statut d'un utilisateur (RG4)
     * Appelé lors de la connexion/déconnexion
     *
     * @param userId L'ID de l'utilisateur
     * @param status Le nouveau statut (ONLINE/OFFLINE)
     */
    public void updateStatus(Long userId, UserStatus status) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // Récupération de l'utilisateur
            User user = session.get(User.class, userId);

            if (user != null) {
                // Modification du statut
                user.setStatus(status);

                // Mise à jour en base (UPDATE)
                session.update(user);

                System.out.println("Statut mis à jour pour " + user.getUsername() + ": " + status);
            }

            transaction.commit();

        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            System.err.println("Erreur lors de la mise à jour du statut: " + e.getMessage());
        }
    }

    /**
     * Authentifie un utilisateur (RG2)
     * Vérifie le nom d'utilisateur et le mot de passe
     *
     * @param username Nom d'utilisateur
     * @param password Mot de passe en clair
     * @return true si l'authentification réussit
     */
    public boolean authenticate(String username, String password) {
        User user = findByUsername(username);

        if (user != null) {
            // Vérification du mot de passe hashé avec BCrypt (RG9)
            return PasswordUtil.checkPassword(password, user.getPassword());
        }

        return false; // Utilisateur non trouvé
    }

    /**
     * Enregistre un nouvel utilisateur (RG1, RG9)
     * Vérifie l'unicité et hache le mot de passe
     *
     * @param username Nom d'utilisateur souhaité
     * @param password Mot de passe en clair
     * @return true si l'enregistrement réussit
     */
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
