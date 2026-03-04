package com.messagerie.server;

import com.messagerie.dao.MessageDAO;
import com.messagerie.dao.UserDAO;
import com.messagerie.model.Message;
import com.messagerie.model.User;
import com.messagerie.model.UserStatus;
import com.messagerie.model.MessageStatus; // IMPORTANT: Ajouter cet import manquant
import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Gestionnaire d'un client connecté au serveur
 * Chaque client connecté a sa propre instance de cette classe
 * S'exécute dans un thread dédié (RG11)
 *
 * Rôles principaux:
 * - Authentifier l'utilisateur
 * - Traiter les requêtes (envoi message, historique, etc.)
 * - Gérer la déconnexion
 * - Maintenir la communication avec le client
 *
 * @author MessagerieApp
 * @version 1.0
 */
public class ClientHandler implements Runnable {

    // =============== ATTRIBUTS ===============

    private Socket socket;              // Socket de communication avec le client
    private PrintWriter out;             // Flux de sortie vers le client (envoi de données)
    private BufferedReader in;           // Flux d'entrée du client (réception de données)
    private User currentUser;             // Utilisateur actuellement connecté (null si non authentifié)
    private Server server;                 // Référence au serveur principal
    private UserDAO userDAO;               // DAO pour les opérations utilisateur
    private MessageDAO messageDAO;         // DAO pour les opérations message

    // =============== CONSTRUCTEUR ===============

    /**
     * Constructeur du gestionnaire de client
     * @param socket Le socket de communication avec le client
     * @param server Référence au serveur principal (pour accéder à la liste des utilisateurs en ligne)
     */
    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.userDAO = new UserDAO();       // Initialisation du DAO utilisateur
        this.messageDAO = new MessageDAO(); // Initialisation du DAO message
    }

    // =============== MÉTHODE PRINCIPALE ===============

    /**
     * Point d'entrée du thread (méthode appelée par Thread.start())
     * RG11: Chaque client est géré dans un thread séparé
     *
     * Cette méthode:
     * 1. Initialise les flux de communication
     * 2. Attend et traite les requêtes du client en boucle
     * 3. Gère la déconnexion en cas d'erreur
     */
    @Override
    public void run() {
        try {
            // Initialisation des flux de communication
            // in: pour lire les données envoyées par le client
            // out: pour envoyer des données au client
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true); // true = auto-flush

            String request;
            // Boucle infinie: tant que le client est connecté, on lit ses requêtes
            // in.readLine() est bloquant: attend que le client envoie une ligne
            // Retourne null quand le client ferme la connexion
            while ((request = in.readLine()) != null) {
                handleRequest(request); // Traitement de la requête
            }
        } catch (IOException e) {
            // RG10: En cas de perte de connexion
            System.out.println("Client déconnecté: " + e.getMessage());
        } finally {
            // Nettoyage: mise à jour du statut et fermeture des ressources
            disconnect();
        }
    }

    // =============== GESTION DES REQUÊTES ===============

    /**
     * Analyse et traite une requête reçue du client
     * Le format attendu est: "COMMANDE|param1|param2|..."
     *
     * @param request La requête brute du client
     */
    private void handleRequest(String request) {
        // Découpage de la requête selon le séparateur "|"
        // Exemple: "LOGIN|alice|password123" -> ["LOGIN", "alice", "password123"]
        String[] parts = request.split("\\|");
        String command = parts[0]; // La commande est toujours le premier élément

        // Traitement selon le type de commande
        switch (command) {
            case "LOGIN":
                handleLogin(parts);
                break;
            case "REGISTER":
                handleRegister(parts);
                break;
            case "SEND_MESSAGE":
                handleSendMessage(parts);
                break;
            case "GET_HISTORY":
                handleGetHistory(parts);
                break;
            case "GET_ONLINE_USERS":
                handleGetOnlineUsers();
                break;
            case "LOGOUT":
                handleLogout();
                break;
            default:
                // Commande inconnue
                out.println("ERROR|Commande inconnue");
        }
    }

    // =============== GESTION DE L'AUTHENTIFICATION ===============

    /**
     * Traite une demande de connexion
     * RG2: Un utilisateur doit être authentifié pour utiliser l'application
     * RG3: Un utilisateur ne peut être connecté qu'une seule fois
     * RG4: À la connexion le statut devient ONLINE
     *
     * @param parts Tableau contenant [LOGIN, username, password]
     */
    private void handleLogin(String[] parts) {
        String username = parts[1];
        String password = parts[2];

        // Vérification des identifiants via le DAO (RG2)
        if (userDAO.authenticate(username, password)) {
            User user = userDAO.findByUsername(username);

            // RG3: Vérifier si l'utilisateur n'est pas déjà connecté
            if (server.isUserOnline(username)) {
                out.println("LOGIN_FAILED|User already connected");
                return;
            }

            // Connexion réussie
            this.currentUser = user; // Mémoriser l'utilisateur connecté

            // RG4: Mise à jour du statut en ligne dans la base de données
            userDAO.updateStatus(user.getId(), UserStatus.ONLINE);

            // Ajout à la liste des utilisateurs en ligne du serveur
            server.addOnlineUser(username, this);

            // RG6: Envoyer les messages reçus pendant l'absence
            sendPendingMessages();

            // Confirmation au client
            out.println("LOGIN_SUCCESS|" + username);

            // RG12: Journalisation de la connexion
            server.log("User logged in: " + username);

            // Notifier tous les autres clients du nouveau statut
            server.broadcastUserStatus(username, "ONLINE");
        } else {
            // Échec de l'authentification
            out.println("LOGIN_FAILED|Invalid credentials");
        }
    }

    /**
     * Traite une demande d'inscription
     * RG1: Le username doit être unique
     * RG9: Les mots de passe sont stockés hachés
     *
     * @param parts Tableau contenant [REGISTER, username, password]
     */
    private void handleRegister(String[] parts) {
        String username = parts[1];
        String password = parts[2];

        // RG1: Vérification d'unicité (faite dans register())
        // RG9: Le mot de passe est haché par BCrypt dans register()
        if (userDAO.register(username, password)) {
            out.println("REGISTER_SUCCESS");
            // RG12: Journalisation de l'inscription
            server.log("New user registered: " + username);
        } else {
            out.println("REGISTER_FAILED|Username already exists");
        }
    }

    // =============== GESTION DES MESSAGES ===============

    /**
     * Traite l'envoi d'un message
     * RG5: L'expéditeur doit être connecté, le destinataire doit exister
     * RG7: Le contenu ne doit pas être vide ni dépasser 1000 caractères
     * RG6: Si le destinataire est hors ligne, le message est stocké
     *
     * @param parts Tableau contenant [SEND_MESSAGE, destinataire, contenu]
     */
    private void handleSendMessage(String[] parts) {
        // RG5: Vérification que l'utilisateur est authentifié
        if (currentUser == null) {
            out.println("ERROR|Not authenticated");
            return;
        }

        String receiverUsername = parts[1];
        String content = parts[2];

        // RG7: Validation du contenu - non vide
        if (content == null || content.trim().isEmpty()) {
            out.println("ERROR|Message cannot be empty");
            return;
        }

        // RG7: Validation du contenu - longueur maximale
        if (content.length() > 1000) {
            out.println("ERROR|Message too long (max 1000 characters)");
            return;
        }

        // RG5: Vérifier que le destinataire existe dans la base
        User receiver = userDAO.findByUsername(receiverUsername);
        if (receiver == null) {
            out.println("ERROR|Receiver does not exist");
            return;
        }

        // Création et sauvegarde du message
        Message message = new Message(currentUser, receiver, content);
        messageDAO.save(message); // Sauvegarde en base

        // RG6: Si le destinataire est en ligne, lui envoyer directement
        ClientHandler receiverHandler = server.getClientHandler(receiverUsername);
        if (receiverHandler != null) {
            // Envoi immédiat
            receiverHandler.sendMessage(currentUser.getUsername(), content, message.getId());
            // Mise à jour du statut (ENVOYE -> RECU)
            messageDAO.updateMessageStatus(message.getId(), MessageStatus.RECU);
        }
        // Si destinataire hors ligne, le message reste en base avec statut ENVOYE

        // Confirmation à l'expéditeur
        out.println("MESSAGE_SENT|" + message.getId());

        // RG12: Journalisation de l'envoi
        server.log("Message sent from " + currentUser.getUsername() + " to " + receiverUsername);
    }

    /**
     * Récupère l'historique des messages avec un autre utilisateur
     * RG2: L'utilisateur doit être authentifié
     * RG8: L'historique est affiché par ordre chronologique
     *
     * @param parts Tableau contenant [GET_HISTORY, autre_utilisateur]
     */
    private void handleGetHistory(String[] parts) {
        // RG2: Vérification de l'authentification
        if (currentUser == null) {
            out.println("ERROR|Not authenticated");
            return;
        }

        String otherUsername = parts[1];
        User otherUser = userDAO.findByUsername(otherUsername);

        if (otherUser != null) {
            // RG8: Récupération de la conversation triée par date
            List<Message> conversation = messageDAO.getConversation(currentUser, otherUser);

            // Envoi de l'historique au client
            out.println("HISTORY_START"); // Marqueur de début

            for (Message msg : conversation) {
                String sender = msg.getSender().getUsername();
                String content = msg.getContenu();
                String date = msg.getDateEnvoi().toString();
                // Format: expéditeur|contenu|date
                out.println(sender + "|" + content + "|" + date);
            }

            out.println("HISTORY_END"); // Marqueur de fin
        }
    }

    /**
     * Envoie la liste des utilisateurs en ligne au client
     * RG2: L'utilisateur doit être authentifié
     */
    private void handleGetOnlineUsers() {
        // RG2: Vérification de l'authentification
        if (currentUser == null) {
            out.println("ERROR|Not authenticated");
            return;
        }

        // Récupération de la liste depuis le serveur
        List<String> onlineUsers = server.getOnlineUsers();

        // Envoi au format "ONLINE_USERS|user1,user2,user3"
        out.println("ONLINE_USERS|" + String.join(",", onlineUsers));
    }

    /**
     * Traite une demande de déconnexion volontaire
     */
    private void handleLogout() {
        if (currentUser != null) {
            disconnect(); // Appel à la méthode de déconnexion
        }
    }

    // =============== GESTION DES MESSAGES EN ATTENTE ===============

    /**
     * Envoie les messages en attente à un utilisateur qui se connecte
     * RG6: Les messages sont livrés lors de la prochaine connexion
     *
     * Cette méthode est appelée automatiquement après une connexion réussie
     */
    private void sendPendingMessages() {
        // Récupération des messages en attente (statut ENVOYE)
        List<Message> pendingMessages = messageDAO.getPendingMessages(currentUser);

        for (Message msg : pendingMessages) {
            // Envoi du message au client
            sendMessage(msg.getSender().getUsername(), msg.getContenu(), msg.getId());

            // Mise à jour du statut: ENVOYE -> RECU
            messageDAO.updateMessageStatus(msg.getId(), MessageStatus.RECU);
        }
    }

    // =============== ENVOI DE MESSAGES AU CLIENT ===============

    /**
     * Envoie un message au client
     * Utilisé pour les messages en temps réel et les messages en attente
     *
     * @param sender Nom de l'expéditeur
     * @param content Contenu du message
     * @param messageId ID du message
     */
    public void sendMessage(String sender, String content, Long messageId) {
        out.println("NEW_MESSAGE|" + sender + "|" + content + "|" + messageId);
    }

    // =============== GESTION DE LA DÉCONNEXION ===============

    /**
     * Déconnecte proprement le client
     * RG4: À la déconnexion le statut devient OFFLINE
     * RG10: Gestion de la perte de connexion
     *
     * Cette méthode est appelée:
     * - Quand le client se déconnecte volontairement
     * - En cas de perte de connexion (RG10)
     */
    private void disconnect() {
        if (currentUser != null) {
            // RG4: Mise à jour du statut en hors ligne
            userDAO.updateStatus(currentUser.getId(), UserStatus.OFFLINE);

            // Retrait de la liste des utilisateurs en ligne
            server.removeOnlineUser(currentUser.getUsername());

            // Notification aux autres clients
            server.broadcastUserStatus(currentUser.getUsername(), "OFFLINE");

            // RG12: Journalisation de la déconnexion
            server.log("User disconnected: " + currentUser.getUsername());

            currentUser = null; // Plus d'utilisateur connecté
        }

        // Fermeture des ressources réseau
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
