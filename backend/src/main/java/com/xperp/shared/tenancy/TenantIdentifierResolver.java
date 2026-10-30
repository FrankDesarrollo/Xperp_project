package com.xperp.shared.tenancy;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Le dice a Hibernate qué empresa usar para las entidades con {@code @TenantId}. Sin empresa en el
 * contexto devuelve {@link TenantContext#ROOT}, que no filtra (código de sistema y arranque).
 */
@Component
class TenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

  @Override
  public Long resolveCurrentTenantIdentifier() {
    Long current = TenantContext.current();
    return current == null ? TenantContext.ROOT : current;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }

  @Override
  public boolean isRoot(Long tenantId) {
    return TenantContext.ROOT.equals(tenantId);
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
