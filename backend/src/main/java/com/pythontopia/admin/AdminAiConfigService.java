package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAiConfigService {
  private static final String PROVIDER = "DeepSeek";
  private static final String FLASH = "deepseek-flash";
  private static final String PRO = "deepseek-v4-pro";
  private final AdminAiConfigRepository repository;
  private final AdminAiConfigGraphClient graph;
  private final AdminAiConfigEncryption encryption;

  @Autowired
  public AdminAiConfigService(
      AdminAiConfigRepository repository,
      AdminAiConfigGraphClient graph,
      @Value("${pythontopia.admin.encryption-key:}") String encryptionKey) {
    this(repository, graph, new AdminAiConfigEncryption(encryptionKey));
  }

  AdminAiConfigService(AdminAiConfigRepository repository, AdminAiConfigGraphClient graph, AdminAiConfigEncryption encryption) {
    this.repository = repository;
    this.graph = graph;
    this.encryption = encryption;
  }

  public AdminAiConfigDto get(CurrentUserPrincipal user) {
    requireAdmin(user);
    Optional<AdminAiConfigRepository.Stored> stored = repository.find();
    AdminAiConfigGraphClient.GraphStatus live = graph.status();
    return new AdminAiConfigDto(
        PROVIDER,
        stored.map(AdminAiConfigRepository.Stored::model).orElse(live.model()),
        stored.map(row -> row.encryptedApiKey() != null || live.apiKeyConfigured()).orElse(live.apiKeyConfigured()),
        stored.map(row -> row.encryptedApiKey() != null).orElse(false),
        stored.map(AdminAiConfigRepository.Stored::updatedAt).orElse(null));
  }

  public AdminAiConfigGraphClient.GraphTestResult test(CurrentUserPrincipal user, String model, String apiKey) {
    requireAdmin(user);
    requireModel(model);
    String key = apiKey == null || apiKey.isBlank() ? decryptSavedKey() : apiKey.trim();
    return graph.test(model, key);
  }

  public void restoreSavedConfiguration() {
    repository.find().ifPresent(
        stored -> {
          String key = stored.encryptedApiKey() == null ? null : encryption.decrypt(stored.encryptedApiKey());
          graph.apply(stored.model(), key);
        });
  }

  @Transactional
  public AdminAiConfigDto update(CurrentUserPrincipal user, String model, String apiKey) {
    requireAdmin(user);
    requireModel(model);
    Optional<AdminAiConfigRepository.Stored> current = repository.find();
    String normalizedKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
    String cipher = normalizedKey == null
        ? current.map(AdminAiConfigRepository.Stored::encryptedApiKey).orElse(null)
        : encryption.encrypt(normalizedKey);
    String effectiveKey = normalizedKey == null && cipher != null ? encryption.decrypt(cipher) : normalizedKey;
    AdminAiConfigGraphClient.GraphStatus live = graph.apply(model, effectiveKey);
    AdminAiConfigRepository.Stored saved = repository.save(model, cipher, user.userId());
    return new AdminAiConfigDto(PROVIDER, saved.model(), cipher != null || live.apiKeyConfigured(), cipher != null, saved.updatedAt());
  }

  private String decryptSavedKey() {
    return repository.find().map(AdminAiConfigRepository.Stored::encryptedApiKey).map(encryption::decrypt).orElse(null);
  }

  private void requireAdmin(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "仅管理员可管理 AI 配置");
    }
  }

  private void requireModel(String model) {
    if (!FLASH.equals(model) && !PRO.equals(model)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "AI_MODEL_UNSUPPORTED", "仅支持 DeepSeek 当前可用模型");
    }
  }
}
