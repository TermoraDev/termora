package app.termora;

import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.session.SessionContext;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.*;

@Deprecated
public class CombinedKeyIdentityProvider implements KeyIdentityProvider {

    private final List<KeyIdentityProvider> providers = new ArrayList<>();

    @Override
    public Iterable<KeyPair> loadKeys(SessionContext context) {
        return () -> new Iterator<>() {

            private final Iterator<KeyIdentityProvider> factories = providers
                    .iterator();
            private Iterator<KeyPair> current;

            private Boolean hasElement;

            @Override
            public boolean hasNext() {
                if (hasElement != null) {
                    return hasElement;
                }
                while (current == null || !current.hasNext()) {
                    if (factories.hasNext()) {
                        try {
                            current = factories.next().loadKeys(context)
                                    .iterator();
                        } catch (IOException | GeneralSecurityException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        current = null;
                        hasElement = Boolean.FALSE;
                        return false;
                    }
                }
                hasElement = Boolean.TRUE;
                return true;
            }

            @Override
            public KeyPair next() {
                if ((hasElement == null && !hasNext()) || !hasElement) {
                    throw new NoSuchElementException();
                }
                hasElement = null;
                KeyPair result;
                try {
                    result = current.next();
                } catch (NoSuchElementException e) {
                    result = null;
                }
                return result;
            }

        };
    }

    public void addKeyKeyIdentityProvider(KeyIdentityProvider provider) {
        providers.add(Objects.requireNonNull(provider));
    }
}