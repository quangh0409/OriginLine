package vn.giapha;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.TransportConfig;

/**
 * Chien luoc tim Docker theo <b>docker context dang hoat dong</b> cua Docker CLI.
 *
 * <h2>Bay: "Could not find a valid Docker environment" trong khi `docker ps` chay tot</h2>
 * Testcontainers 1.19.x chua biet toi co che {@code docker context}. Tren Windows no chi thu
 * {@code npipe:////./pipe/docker_engine}. Nhung Docker Desktop ban moi dat context mac dinh la
 * {@code desktop-linux} tro toi {@code npipe:////./pipe/dockerDesktopLinuxEngine}; pipe cu VAN TON
 * TAI nhung tra ve <b>HTTP 400 kem mot khoi {@code /info} rong</b>. Testcontainers doc ket qua do
 * la "khong co Docker", va thong bao loi tro sai huong hoan toan:
 *
 * <pre>
 *   NpipeSocketClientProviderStrategy: failed with exception BadRequestException (Status 400: {...})
 *   Could not find a valid Docker environment.
 * </pre>
 *
 * <p>Dat {@code DOCKER_HOST} bang thuoc tinh he thong <b>khong cuu duoc</b>: Testcontainers doc
 * {@code docker.host} qua {@code getEnvVarOrUserProperty}, tuc chi tu <b>bien moi truong</b> hoac
 * {@code ~/.testcontainers.properties} - khong nhin thuoc tinh he thong. Vi vay loi ra dung dan la
 * mot {@link DockerClientProviderStrategy} rieng, dang ky qua {@code ServiceLoader}
 * ({@code META-INF/services/org.testcontainers.dockerclient.DockerClientProviderStrategy}).</p>
 *
 * <h2>Bo cuc file ma lop nay doc (do Docker CLI dinh nghia)</h2>
 * <ul>
 *   <li>{@code ~/.docker/config.json} → {@code currentContext}</li>
 *   <li>{@code ~/.docker/contexts/meta/<sha256(ten context)>/meta.json} →
 *       {@code Endpoints.docker.Host}</li>
 * </ul>
 *
 * <p>Khong ap dung khi nguoi dung da dat {@code DOCKER_HOST} tuong minh, khi context la
 * {@code default}, hoac khi khong doc duoc file nao - luc do Testcontainers quay ve cac chien luoc
 * mac dinh, von da dung o Linux/CI.</p>
 */
public final class DockerContextClientProviderStrategy extends DockerClientProviderStrategy {

    /** Cao hon moi chien luoc dung san de duoc thu truoc pipe/socket mac dinh. */
    private static final int PRIORITY = 1000;

    static {
        pinDockerApiVersion();
    }

    private final URI dockerHost;
    private final String contextName;

    /**
     * Ghim phien ban Docker API theo dung phien ban ma daemon dang chay.
     *
     * <h2>Bay thu hai, an sau bay thu nhat</h2>
     * Docker Engine 25+ da <b>bo</b> cac phien ban API cu ({@code MinAPIVersion} cua Docker 29 la
     * {@code 1.44}), trong khi docker-java 3.3.6 di kem Testcontainers 1.19.8 van gui duong dan co
     * tien to phien ban cu. Daemon tra ve <b>HTTP 400 kem mot khoi {@code /info} rong</b> - dung
     * cai trieu chung ma Testcontainers dich thanh "Could not find a valid Docker environment".
     *
     * <p>Hoi thang {@code docker version} thay vi ghim mot hang so: ghim {@code 1.44} se hong
     * nguoc lai tren may co Docker cu hon. Khong hoi duoc (khong co {@code docker} tren PATH, CI
     * dung socket thuan) thi khong dat gi ca va de docker-java giu hanh vi mac dinh.</p>
     */
    private static void pinDockerApiVersion() {
        if (System.getProperty("api.version") != null || System.getenv("DOCKER_API_VERSION") != null) {
            return;
        }
        try {
            Process process = new ProcessBuilder("docker", "version", "--format",
                    "{{.Server.APIVersion}}").redirectErrorStream(true).start();
            String output;
            try (var stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            if (process.exitValue() == 0 && output.matches("\\d+\\.\\d+")) {
                System.setProperty("api.version", output);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            // Khong do duoc thi thoi - de docker-java giu mac dinh cua no.
        }
    }

    public DockerContextClientProviderStrategy() {
        String host = null;
        String context = null;
        if (System.getenv("DOCKER_HOST") == null) {
            try {
                Path dockerDir = Path.of(System.getProperty("user.home"), ".docker");
                Path configFile = dockerDir.resolve("config.json");
                if (Files.isReadable(configFile)) {
                    ObjectMapper mapper = new ObjectMapper();
                    context = mapper.readTree(Files.readString(configFile, StandardCharsets.UTF_8))
                            .path("currentContext").asText("");
                    if (!context.isBlank() && !"default".equals(context)) {
                        Path meta = dockerDir.resolve("contexts").resolve("meta")
                                .resolve(sha256Hex(context)).resolve("meta.json");
                        if (Files.isReadable(meta)) {
                            String value = mapper
                                    .readTree(Files.readString(meta, StandardCharsets.UTF_8))
                                    .path("Endpoints").path("docker").path("Host").asText("");
                            host = value.isBlank() ? null : value;
                        }
                    }
                }
            } catch (Exception ex) {
                // Doc duoc thi tot, khong doc duoc thi de chien luoc mac dinh lam viec cua no.
                host = null;
            }
        }
        this.dockerHost = host == null ? null : URI.create(host);
        this.contextName = context;
    }

    @Override
    protected boolean isApplicable() {
        return dockerHost != null;
    }

    @Override
    protected int getPriority() {
        return PRIORITY;
    }

    @Override
    public TransportConfig getTransportConfig() {
        return TransportConfig.builder().dockerHost(dockerHost).build();
    }

    @Override
    public String getDescription() {
        return "Docker context '" + contextName + "' (" + dockerHost + ") tu ~/.docker/config.json";
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
