package com.finsentinel.notification;

import com.finsentinel.auth.MailNotificationService;
import com.finsentinel.auth.UserEntity;
import com.finsentinel.auth.UserPreferencesEntity;
import com.finsentinel.auth.UserPreferencesRepository;
import com.finsentinel.auth.UserRepository;
import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.forecast.ForecastDtos;
import com.finsentinel.forecast.ForecastService;
import com.finsentinel.payment.FinancialObligationEntity;
import com.finsentinel.payment.FinancialObligationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_RETRIES = 3;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository prefsRepository;
    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final FinancialObligationRepository obligationRepository;
    private final ForecastService forecastService;
    private final MailNotificationService mailService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferencesRepository prefsRepository,
                               UserRepository userRepository,
                               UserPreferencesRepository userPreferencesRepository,
                               FinancialObligationRepository obligationRepository,
                               ForecastService forecastService,
                               MailNotificationService mailService) {
        this.notificationRepository = notificationRepository;
        this.prefsRepository = prefsRepository;
        this.userRepository = userRepository;
        this.userPreferencesRepository = userPreferencesRepository;
        this.obligationRepository = obligationRepository;
        this.forecastService = forecastService;
        this.mailService = mailService;
    }

    @Scheduled(fixedDelay = 300000L, initialDelay = 60000L)
    @Transactional
    public void runScheduledReminders() {
        for (UserEntity user : userRepository.findAll()) {
            if (!"ACTIVE".equals(user.getStatus())) continue;
            try {
                evaluateRemindersForUser(user.getId());
            } catch (Exception ex) {
                log.warn("Reminder evaluation failed for user {}: {}", user.getId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public int evaluateRemindersForUser(UUID userId) {
        NotificationPreferencesEntity prefs = prefsRepository.findById(userId).orElseGet(() -> defaultPrefs(userId));
        if (!prefs.isInAppEnabled() && !prefs.isEmailEnabled()) {
            return 0;
        }
        String tz = userPreferencesRepository.findById(userId)
                .map(UserPreferencesEntity::getTimezone)
                .orElse("Asia/Kolkata");
        LocalDate userToday = LocalDate.now(ZoneId.of(tz));
        int windowDays = Math.max(1, prefs.getReminderDaysBefore());

        UserEntity user = userRepository.findById(userId).orElse(null);
        int created = 0;

        for (FinancialObligationEntity ob : obligationRepository.findByUserIdOrderByDueDateAsc(userId)) {
            if ("PAID".equals(ob.getStatus()) || ob.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            boolean isOverdue = ob.getDueDate().isBefore(userToday);
            boolean isSoon = !isOverdue && !ob.getDueDate().isAfter(userToday.plusDays(windowDays));
            if (!isOverdue && !isSoon) continue;

            String dedupKey = "OBLIGATION:" + userId + ":" + ob.getId() + ":" + ob.getDueDate() + ":" + (isOverdue ? "OVERDUE" : "DUE_SOON");
            if (notificationRepository.existsByDedupKey(dedupKey)) {
                continue;
            }

            String title = isOverdue ? "Payment past due date: " + ob.getTitle() : "Upcoming payment reminder: " + ob.getTitle();
            String body = isOverdue
                    ? ob.getTitle() + " (" + ob.getRemainingAmount() + " remaining) was due on " + ob.getDueDate() + ". Review payment planner options."
                    : ob.getTitle() + " (" + ob.getRemainingAmount() + " remaining) is due on " + ob.getDueDate() + ".";

            NotificationEntity notif = new NotificationEntity();
            notif.setId(UUID.randomUUID());
            notif.setUserId(userId);
            notif.setObligationId(ob.getId());
            notif.setDedupKey(dedupKey);
            notif.setNotifType(isOverdue ? "PAYMENT_OVERDUE" : "PAYMENT_DUE_SOON");
            notif.setSeverity(isOverdue ? "WARNING" : "INFO");
            notif.setTitle(title);
            notif.setBody(body);
            notif.setScheduledFor(Instant.now());
            notif.setCreatedAt(Instant.now());

            if (prefs.isEmailEnabled() && user != null && notif.getRetryCount() < MAX_RETRIES) {
                boolean sent = mailService.sendReminderEmail(user.getEmail(), "FinSentinel Payment Reminder",
                        "You have an upcoming scheduled payment reminder in FinSentinel AI. Sign in to review details.");
                notif.setDeliveryChannel(prefs.isInAppEnabled() ? "IN_APP+EMAIL" : "EMAIL");
                notif.setDeliveryStatus(sent ? "DELIVERED" : "IN_APP_ONLY_EMAIL_FAILED");
                if (!sent) notif.setRetryCount(1);
            } else {
                notif.setDeliveryChannel("IN_APP");
                notif.setDeliveryStatus("DELIVERED");
            }
            notificationRepository.save(notif);
            created++;
        }

        if (prefs.isShortfallAlertsEnabled()) {
            ForecastDtos.ForecastResultDto forecast = forecastService.generateForecast(userId, 30, userToday, false);
            if (!forecast.insufficientData() && forecast.shortageDate() != null) {
                String dedupKey = "SHORTFALL:" + userId + ":" + userToday + ":" + forecast.shortageDate();
                if (!notificationRepository.existsByDedupKey(dedupKey)) {
                    NotificationEntity notif = new NotificationEntity();
                    notif.setId(UUID.randomUUID());
                    notif.setUserId(userId);
                    notif.setDedupKey(dedupKey);
                    notif.setNotifType("PROJECTED_SHORTFALL");
                    notif.setSeverity("CRITICAL");
                    notif.setTitle("Projected liquidity shortfall around " + forecast.shortageDate());
                    notif.setBody("Based on current scheduled inflows and obligations, projected balance dips to "
                            + forecast.minProjectedBalance() + ". Open Digital Twin or Payment Planner to inspect scenarios.");
                    notif.setDeliveryChannel("IN_APP");
                    notif.setDeliveryStatus("DELIVERED");
                    notif.setScheduledFor(Instant.now());
                    notif.setCreatedAt(Instant.now());
                    notificationRepository.save(notif);
                    created++;
                }
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<NotificationEntity> listNotifications(UUID userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public NotificationEntity markRead(UserSessionPrincipal principal, UUID id, boolean read) {
        NotificationEntity n = notificationRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        n.setRead(read);
        return notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        List<NotificationEntity> all = notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        for (NotificationEntity n : all) {
            if (!n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        }
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesEntity getPreferences(UUID userId) {
        return prefsRepository.findById(userId).orElseGet(() -> defaultPrefs(userId));
    }

    @Transactional
    public NotificationPreferencesEntity updatePreferences(UUID userId, boolean inApp, boolean email, int daysBefore, boolean shortfallAlerts) {
        NotificationPreferencesEntity prefs = prefsRepository.findById(userId).orElseGet(() -> defaultPrefs(userId));
        prefs.setInAppEnabled(inApp);
        prefs.setEmailEnabled(email);
        prefs.setReminderDaysBefore(Math.max(1, Math.min(30, daysBefore)));
        prefs.setShortfallAlertsEnabled(shortfallAlerts);
        prefs.setUpdatedAt(Instant.now());
        return prefsRepository.save(prefs);
    }

    private NotificationPreferencesEntity defaultPrefs(UUID userId) {
        NotificationPreferencesEntity p = new NotificationPreferencesEntity();
        p.setUserId(userId);
        p.setUpdatedAt(Instant.now());
        return p;
    }
}
