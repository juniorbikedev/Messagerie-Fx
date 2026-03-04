package com.messagerie.server;

import com.messagerie.dao.MessageDAO;
import com.messagerie.dao.UserDAO;
import com.messagerie.model.Message;
import com.messagerie.model.User;
import com.messagerie.model.UserStatus;
import com.messagerie.model.MessageStatus;
import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {


    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private User currentUser;
    private Server server;
    private UserDAO userDAO;
    private MessageDAO messageDAO;


    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.userDAO = new UserDAO();
        this.messageDAO = new MessageDAO();
    }

    @Override
    public void run() {
        try {

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String request;

            while ((request = in.readLine()) != null) {
                handleRequest(request);
            }
        } catch (IOException e) {
            System.out.println("Client déconnecté: " + e.getMessage());
        } finally {
            disconnect();
        }
    }


    private void handleRequest(String request) {

        String[] parts = request.split("\\|");
        String command = parts[0];

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

                out.println("ERROR|Commande inconnue");
        }
    }




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


            this.currentUser = user;

            // RG4: Mise à jour du statut en ligne dans la base de données
            userDAO.updateStatus(user.getId(), UserStatus.ONLINE);

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
        messageDAO.save(message);

        // RG6: Si le destinataire est en ligne, lui envoyer directement
        ClientHandler receiverHandler = server.getClientHandler(receiverUsername);
        if (receiverHandler != null) {
            receiverHandler.sendMessage(currentUser.getUsername(), content, message.getId());
            messageDAO.updateMessageStatus(message.getId(), MessageStatus.RECU);
        }

        out.println("MESSAGE_SENT|" + message.getId());

        // RG12: Journalisation de l'envoi
        server.log("Message sent from " + currentUser.getUsername() + " to " + receiverUsername);
    }


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
            out.println("HISTORY_START");

            for (Message msg : conversation) {
                String sender = msg.getSender().getUsername();
                String content = msg.getContenu();
                String date = msg.getDateEnvoi().toString();

                out.println(sender + "|" + content + "|" + date);
            }

            out.println("HISTORY_END");
        }
    }

    private void handleGetOnlineUsers() {
        // RG2: Vérification de l'authentification
        if (currentUser == null) {
            out.println("ERROR|Not authenticated");
            return;
        }

        // Récupération de la liste depuis le serveur
        List<String> onlineUsers = server.getOnlineUsers();

        out.println("ONLINE_USERS|" + String.join(",", onlineUsers));
    }


    private void handleLogout() {
        if (currentUser != null) {
            disconnect(); // Appel à la méthode de déconnexion
        }
    }

    private void sendPendingMessages() {

        List<Message> pendingMessages = messageDAO.getPendingMessages(currentUser);

        for (Message msg : pendingMessages) {
            sendMessage(msg.getSender().getUsername(), msg.getContenu(), msg.getId());
            messageDAO.updateMessageStatus(msg.getId(), MessageStatus.RECU);
        }
    }


    public void sendMessage(String sender, String content, Long messageId) {
        out.println("NEW_MESSAGE|" + sender + "|" + content + "|" + messageId);
    }


    private void disconnect() {
        if (currentUser != null) {
            // RG4: Mise à jour du statut en hors ligne
            userDAO.updateStatus(currentUser.getId(), UserStatus.OFFLINE);
            server.removeOnlineUser(currentUser.getUsername());
            server.broadcastUserStatus(currentUser.getUsername(), "OFFLINE");
            // RG12: Journalisation de la déconnexion
            server.log("User disconnected: " + currentUser.getUsername());
            currentUser = null;
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
