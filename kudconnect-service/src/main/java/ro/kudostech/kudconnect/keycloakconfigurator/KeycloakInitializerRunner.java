package ro.kudostech.kudconnect.keycloakconfigurator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
@Profile("!acceptancetest")
public class KeycloakInitializerRunner implements CommandLineRunner {

  private static final String KEYCLOAK_SERVER_URL = "http://localhost:9080";
  private static final String REALM_NAME = "kudconnect";
  private static final String CLIENT_ID = "kudconnect-webapp";
  private static final String REDIRECT_URL = "http://localhost:3000/*";
  private static final String USER_ID_CLAIM = "user_id";
  private static final List<UserPass> USER_LIST =
      Arrays.asList(new UserPass("admin@test.com", "admin"), new UserPass("user@test.com", "user"));
  private static final String ROLE_USER = "user";
  private static final String ROLE_ADMIN = "admin";

  record UserPass(String id, String password, String email) {
    public UserPass(String email, String password) {
      this(null, password, email);
    }
  }

  private final Keycloak keycloakAdmin;

  @Override
  public void run(String... args) {
    log.info("Initializing '{}' realm in Keycloak ...", REALM_NAME);

    Optional<RealmRepresentation> representationOptional =
        keycloakAdmin.realms().findAll().stream()
            .filter(r -> r.getRealm().equals(REALM_NAME))
            .findAny();
    if (representationOptional.isPresent()) {
      log.info("Removing already pre-configured '{}' realm", REALM_NAME);
      keycloakAdmin.realm(REALM_NAME).remove();
    }

    // Realm
    RealmRepresentation realmRepresentation = new RealmRepresentation();
    realmRepresentation.setRealm(REALM_NAME);
    realmRepresentation.setEnabled(true);
    realmRepresentation.setRegistrationAllowed(true);
    realmRepresentation.setLoginWithEmailAllowed(true);
    realmRepresentation.setRegistrationEmailAsUsername(true);

    // Configure aditional claims
    ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
    mapper.setName("additional-claims-mapper");
    mapper.setProtocol("openid-connect");
    //    mapper.setProtocolMapper("oidc-usermodel-attribute-mapper");
    mapper.setProtocolMapper("custom-protocol-mapper");

    Map<String, String> config = new HashMap<>();
    config.put("user.attribute", USER_ID_CLAIM);
    config.put("access.token.claim", "true");
    config.put("claim.name", USER_ID_CLAIM);
    config.put("jsonType.label", "String");
    mapper.setConfig(config);

    // Client
    ClientRepresentation clientRepresentation = new ClientRepresentation();
    clientRepresentation.setClientId(CLIENT_ID);
    clientRepresentation.setDirectAccessGrantsEnabled(true);
    clientRepresentation.setPublicClient(true);
    clientRepresentation.setRedirectUris(List.of(REDIRECT_URL));
    clientRepresentation.setDefaultRoles(new String[] {ROLE_USER});
    clientRepresentation.setProtocolMappers(List.of(mapper));

    // Confidential client for internal app communication
    ClientRepresentation confidentialClientRepresentation = new ClientRepresentation();
    confidentialClientRepresentation.setClientId("keycloak-client");
    confidentialClientRepresentation.setSecret("keycloak-dummy-secret"); // set a secret here
    confidentialClientRepresentation.setServiceAccountsEnabled(true);
    confidentialClientRepresentation.setDirectAccessGrantsEnabled(true);
    confidentialClientRepresentation.setPublicClient(false); // it is not a public client
    confidentialClientRepresentation.setRedirectUris(List.of(REDIRECT_URL));

    realmRepresentation.setClients(List.of(clientRepresentation, confidentialClientRepresentation));

    // Users
    List<UserRepresentation> userRepresentations =
        USER_LIST.stream()
            .map(
                userPass -> {
                  // User Credentials
                  CredentialRepresentation credentialRepresentation =
                      new CredentialRepresentation();
                  credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
                  credentialRepresentation.setValue(userPass.password());

                  // User
                  UserRepresentation userRepresentation = new UserRepresentation();
                  userRepresentation.setUsername(userPass.email());
                  userRepresentation.setEmail(userPass.email());
                  userRepresentation.setEnabled(true);
                  userRepresentation.setCredentials(List.of(credentialRepresentation));
                  userRepresentation.setClientRoles(getClientRoles(userPass));
                  return userRepresentation;
                })
            .toList();
    realmRepresentation.setUsers(userRepresentations);

    // Create Realm
    keycloakAdmin.realms().create(realmRepresentation);

    // Testing
    UserPass admin = USER_LIST.get(0);
    log.info("Testing getting token for '{}' ...", admin.email());

    Keycloak kudconnectwebapp =
        KeycloakBuilder.builder()
            .serverUrl(KEYCLOAK_SERVER_URL)
            .realm(REALM_NAME)
            .username(admin.email())
            .password(admin.password())
            .clientId(CLIENT_ID)
            .build();

    log.info(
        "'{}' token: {}", admin.email(), kudconnectwebapp.tokenManager().grantToken().getToken());
    log.info("'{}' initialization completed successfully!", REALM_NAME);
  }

  private Map<String, List<String>> getClientRoles(UserPass userPass) {
    List<String> roles = new ArrayList<>();
    roles.add(ROLE_USER);
    if ("admin".equals(userPass.email())) {
      roles.add(ROLE_ADMIN);
    }
    return Map.of(CLIENT_ID, roles);
  }
}
