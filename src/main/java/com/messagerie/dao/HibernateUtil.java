package com.messagerie.dao;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * Classe utilitaire pour gérer la SessionFactory Hibernate
 * Pattern Singleton pour avoir une seule instance de SessionFactory
 */
public class HibernateUtil {

    // SessionFactory est thread-safe, une seule instance pour toute l'application
    private static final SessionFactory sessionFactory = buildSessionFactory();

    /**
     * Construit la SessionFactory à partir du fichier de configuration
     * @return SessionFactory configurée pour PostgreSQL
     */
    private static SessionFactory buildSessionFactory() {
        try {
            // Charge la configuration depuis hibernate.cfg.xml
            // Ce fichier contient les paramètres de connexion PostgreSQL
            return new Configuration().configure().buildSessionFactory();
        } catch (Throwable ex) {
            // Log détaillé en cas d'erreur de connexion à PostgreSQL
            System.err.println("Échec de l'initialisation de SessionFactory: " + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    /**
     * Retourne la SessionFactory unique
     * @return SessionFactory Hibernate
     */
    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * Ferme proprement la SessionFactory à l'arrêt de l'application
     * À appeler dans le shutdown hook du serveur
     */
    public static void shutdown() {
        getSessionFactory().close();
        System.out.println("SessionFactory fermée - Connexion PostgreSQL terminée");
    }
}