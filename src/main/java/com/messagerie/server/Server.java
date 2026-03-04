package com.messagerie.server;

// Import des classes nécessaires
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

public class Server {

    // Port sur lequel le serveur va écouter
    private static final int PORT = 12345;

    // Socket principal du serveur (attend les connexions)
    private ServerSocket serverSocket;

    // Stocke les utilisateurs connectés (username → ClientHandler)
    private ConcurrentHashMap<String, ClientHandler> onlineUsers;

    // Logger pour enregistrer les événements du serveur
    private static final Logger logger = Logger.getLogger(Server.class.getName());

    // Constructeur
    public Server() {
        onlineUsers = new ConcurrentHashMap<>();
        setupLogger(); // Configuration du système de log
    }

    // Méthode pour configurer le logger
    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("server.log", true);

            fileHandler.setFormatter(new SimpleFormatter());

            logger.addHandler(fileHandler);

            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Méthode principale pour démarrer le serveur
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            log("Server started on port " + PORT);

            // Boucle infinie pour accepter plusieurs clients
            while (true) {

                Socket clientSocket = serverSocket.accept();
                log("New client connected: " + clientSocket.getInetAddress());

                // RG11: Chaque client est géré dans un thread séparé
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);

                Thread thread = new Thread(clientHandler);
                thread.start();
            }

        } catch (IOException e) {
            log("Server error: " + e.getMessage());
        }
    }

    // Ajouter un utilisateur à la liste des connectés
    public void addOnlineUser(String username, ClientHandler handler) {
        onlineUsers.put(username, handler);
    }

    // Supprimer un utilisateur de la liste
    public void removeOnlineUser(String username) {
        onlineUsers.remove(username);
    }

    // Vérifier si un utilisateur est en ligne
    public boolean isUserOnline(String username) {
        return onlineUsers.containsKey(username);
    }

    // Récupérer le ClientHandler d’un utilisateur
    public ClientHandler getClientHandler(String username) {
        return onlineUsers.get(username);
    }

    // Retourner la liste des utilisateurs connectés
    public List<String> getOnlineUsers() {
        return List.copyOf(onlineUsers.keySet());
    }

    // Informer tous les utilisateurs qu’un utilisateur a changé de statut
    public void broadcastUserStatus(String username, String status) {

        // Parcourt tous les utilisateurs connectés
        for (ClientHandler handler : onlineUsers.values()) {

            // Envoie un message de type STATUS_UPDATE
            handler.sendMessage("STATUS_UPDATE", username + "|" + status, -1L);
        }
    }

    // Méthode pour écrire un message dans les logs et la console
    public void log(String message) {
        logger.info(message);
        System.out.println("[SERVER] " + message);
    }

    // Méthode principale (point d’entrée du programme)
    public static void main(String[] args) {
        Server server = new Server();
        server.start(); // Démarrage du serveur
    }
}
