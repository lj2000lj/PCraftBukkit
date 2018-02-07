package cn.apisium.pcraft;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.eclipsesource.v8.*;
import org.bukkit.event.*;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import io.alicorn.v8.V8JavaAdapter;

final public class Main extends JavaPlugin implements Listener {
	private V8 v8Runtime = null;
	private NodeJS nodeRuntime = null;
	private V8Object app = null;
	private final EventRegister register = new EventRegister(new RegisteredListener(
			this,
			(listen, event) -> this.onFire(event),
			EventPriority.MONITOR,
			this,
			false
	));

	@Override
	public void onLoad() {
		nodeRuntime = NodeJS.createNodeJS();
		v8Runtime = nodeRuntime.getRuntime();
	}

	@Override
	public void onDisable() {
		register.unRegisterAll();
		if (app != null) {
			app.executeVoidFunction("disable", null);
			app.release();
		}
	}

	@Override
	public void onEnable() {
		this.getLogger().info("Loading...");

		this.saveResource("setup-script.js", true);

		final String config = this.getPackage();
		if (config == null) return;
		this.checkModules();

		final V8Object obj = new V8Object(v8Runtime);
		final V8Array args = new V8Array(v8Runtime);

		V8JavaAdapter.injectObject("__server", this.getServer(), v8Runtime);
		V8JavaAdapter.injectObject("__helpers", new PCraftHelper(), v8Runtime);

		obj
				.add("pkg", config)
				.add("server", v8Runtime.getObject("__server"))
				.add("helpers", v8Runtime.getObject("__helpers"));
		obj.registerJavaMethod((JavaVoidCallback) (a, b) -> {
			int l = b.length();
			for (int i = 0; i < l; i++) {
				try {
					register.register(b.getString(i));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, "register");
		args.push(obj);

		v8Runtime.addUndefined("__server");
		v8Runtime.addUndefined("__helpers");

		final V8Object app = (V8Object) ((V8Function) nodeRuntime
				.require(new File(this.getDataFolder(), "setup-script.js")))
				.call(null, args);

		if (app.isUndefined()) {
			this.setEnabled(false);
			return;
		}

		obj.release();
		args.release();
		this.app = app;

		while (nodeRuntime.isRunning()) nodeRuntime.handleMessage();
		this.getLogger().info("Loaded successful!");
	}

	private void onFire(Object event) {
		final V8Array args = new V8Array(this.v8Runtime);

		final String id = "event_" + event.hashCode();
		V8JavaAdapter.injectObject(id, event, v8Runtime);

		args.push(v8Runtime.getObject(id));
		v8Runtime.addUndefined(id);

		app.executeVoidFunction("emit", args);
		args.release();
	}

	private String getPackage () {
		if (!this.write("package.json")) {
			this.setEnabled(false);
			return null;
		}
		this.write(".npmrc");

		try {
			return new String(Files.readAllBytes(Paths.get(
					System.getProperty("user.dir"), "package.json")));
		} catch (Exception e) {
			e.printStackTrace();
			this.setEnabled(false);
			return null;
		}
	}

	private void checkModules () {
		if (!new File(System.getProperty("user.dir"), "node_modules/babel-polyfill")
				.isDirectory() && !Installer.install()) {
			this.getLogger().warning("Cannot to install all modules!");
		}
	}

	private boolean write (String name) {
		Path pkg = Paths.get(System.getProperty("user.dir"), name);
		if (!pkg.toFile().isFile()) {
			try (InputStream in = this.getResource(name)) {
				Files.copy(in, pkg);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
}
