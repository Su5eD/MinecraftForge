repositories {
	maven {
		url = uri("https://maven.minecraftforge.net/")
	}
	mavenCentral()
}

dependencies {
	implementation("org.ow2.asm:asm:9.1")
	implementation("org.ow2.asm:asm-tree:9.1")
	implementation("net.minecraftforge:srgutils:0.4.+")
}
