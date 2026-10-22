package org.cookie.usercenter.testsupport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only repository: application tests never open a Redis session connection. */
@Configuration(proxyBeanMethods = false)
@Profile("test")
@EnableSpringHttpSession
public class TestSessionConfiguration {
    @Bean
    public MemoryIndexedSessionRepository sessionRepository() {
        return new MemoryIndexedSessionRepository();
    }

    public static class MemoryIndexedSessionRepository implements FindByIndexNameSessionRepository<MapSession> {
        private final Map<String, Session> sessions = new ConcurrentHashMap<>();
        private final MapSessionRepository delegate = new MapSessionRepository(sessions);

        public MemoryIndexedSessionRepository() {
            delegate.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
        }

        @Override public MapSession createSession() { return delegate.createSession(); }
        @Override public void save(MapSession session) { delegate.save(session); }
        @Override public MapSession findById(String id) { return delegate.findById(id); }
        @Override public void deleteById(String id) { delegate.deleteById(id); }

        @Override
        public Map<String, MapSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
            Map<String, MapSession> result = new LinkedHashMap<>();
            if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) return result;
            for (String id : sessions.keySet()) {
                MapSession session = findById(id);
                if (session != null && indexValue.equals(session.getAttribute(indexName))) result.put(id, session);
            }
            return result;
        }
    }
}
