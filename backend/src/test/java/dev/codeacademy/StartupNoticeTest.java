package dev.codeacademy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** H10: plain HTTP is only fine on this machine; on the network, passwords would travel unencrypted. */
class StartupNoticeTest {

	@Test
	void warnsOnlyForPlainHttpBeyondThisMachine() {
		assertThat(StartupNotice.plainHttpOnTheNetwork("http://127.0.0.1:8080")).isFalse();
		assertThat(StartupNotice.plainHttpOnTheNetwork("http://localhost:8080")).isFalse();
		assertThat(StartupNotice.plainHttpOnTheNetwork("https://academia.example.org")).isFalse();
		assertThat(StartupNotice.plainHttpOnTheNetwork("http://192.168.1.50:8080")).isTrue();
		assertThat(StartupNotice.plainHttpOnTheNetwork("http://mi-equipo.local:8080")).isTrue();
		assertThat(StartupNotice.plainHttpOnTheNetwork("no es una dirección")).isFalse();
	}
}
