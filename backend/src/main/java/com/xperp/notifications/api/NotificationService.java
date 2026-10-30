package com.xperp.notifications.api;

/** Notificaciones en la aplicación para personas o roles. */
public interface NotificationService {

  /** Notifica a todos los usuarios activos de un rol (y a los administradores si se pide). */
  void notifyRole(
      String role, String type, String title, String body, String link, boolean alsoAdmins);

  void notifyUser(long userId, String type, String title, String body, String link);
}
