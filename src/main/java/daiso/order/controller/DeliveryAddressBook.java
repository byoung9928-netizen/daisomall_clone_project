package daiso.order.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpSession;

final class DeliveryAddressBook {
    private static final String ENTRIES = "orderDeliveryAddresses";
    private static final String CURRENT = "orderDeliveryAddress";

    private DeliveryAddressBook() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, String>> entries(HttpSession session) {
        Object saved = session.getAttribute(ENTRIES);

        if (saved instanceof Map) {
            return (Map<String, Map<String, String>>) saved;
        }

        Map<String, Map<String, String>> addresses = new LinkedHashMap<>();

        Object current = session.getAttribute(CURRENT);
        if (current instanceof Map) {
            Map<String, String> address =
                    new LinkedHashMap<>((Map<String, String>) current);

            String id = UUID.randomUUID().toString();
            address.put("id", id);
            addresses.put(id, address);
            session.setAttribute(CURRENT, address);
        }

        session.setAttribute(ENTRIES, addresses);
        return addresses;
    }
}