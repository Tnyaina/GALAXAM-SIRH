package mg.galaxam.sirh.paie.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BulletinPaieUpdateAuditMetadataTest {
    @Test
    void updatePersistsCalculationTimestampAndActor() throws Exception {
        String[] sql = new String[1];
        Map<Integer, Object> bindings = new HashMap<>();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("executeUpdate")) return 1;
                    if (method.getName().startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
                        bindings.put(index, args[1]);
                    }
                    return defaultValue(method.getReturnType());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        sql[0] = (String) args[0];
                        return statement;
                    }
                    return defaultValue(method.getReturnType());
                });

        Timestamp calculatedAt = Timestamp.valueOf("2026-10-25 20:30:00");
        BulletinPaie bulletin = new BulletinPaie();
        bulletin.setIdBulletin(77);
        bulletin.setIdPeriodePaie(9);
        bulletin.setIdEmploye(23);
        bulletin.setDateCalcul(calculatedAt);
        bulletin.setCalculePar(12);
        bulletin.update(connection);

        assertTrue(sql[0].contains("date_calcul = ?, calcule_par = ?"));
        int timestampIndex = bindings.entrySet().stream()
                .filter(entry -> calculatedAt.equals(entry.getValue()))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        assertEquals(calculatedAt, bindings.get(timestampIndex));
        assertEquals(12, bindings.get(timestampIndex + 1));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
