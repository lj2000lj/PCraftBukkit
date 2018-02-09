package cn.apisium.pcraft;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.eclipsesource.v8.*;
import org.bukkit.event.*;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import io.alicorn.v8.V8JavaAdapter;

final public class Main extends JavaPlugin implements Listener {
	private V8 v8Runtime = null;
	private NodeJS nodeRuntime = null;
	private V8Object app = null;
	private Commander commander = null;
	private final EventRegister register = new EventRegister(new RegisteredListener(
			this,
			(listen, event) -> {
				final V8Array args = new V8Array(v8Runtime);

				final Object obj = new ObjectProxy(event);
				final String id = "event_" + obj.hashCode();
				V8JavaAdapter.injectObject(id, obj, v8Runtime);
				V8Object o = v8Runtime.getObject(id);

				args.push(o);
				v8Runtime.addUndefined(id);

				app.executeVoidFunction("emit", args);
				o.release();
				args.release();
			},
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
		commander.unRegisterAll();
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

		try {
			this.commander = new Commander(v8Runtime, this);
		} catch (Exception e) {
			e.printStackTrace();
			this.setEnabled(false);
			return;
		}

		if (!this.checkModules().init(config)) return;

		while (nodeRuntime.isRunning()) nodeRuntime.handleMessage();
		this.getLogger().info("Loaded successful!");
	}

	private boolean init(String config) {
		final V8Object obj = new V8Object(v8Runtime);
		final V8Array args = new V8Array(v8Runtime);

		V8JavaAdapter.injectObject("__server", this.getServer(), v8Runtime);
		V8JavaAdapter.injectObject("__helpers", new PCraftHelper(), v8Runtime);

		obj
				.add("pkg", config)
				.add("server", v8Runtime.getObject("__server"))
				.add("helpers", v8Runtime.getObject("__helpers"));
		obj.registerJavaMethod((JavaVoidCallback) (a, b) -> {
			for (String name : b.getStrings(0, b.length())) {
				try {
					register.register(name);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, "register");
		obj.registerJavaMethod((JavaVoidCallback) (a, b) -> {
			V8Array arr = b.getArray(3);
			V8Function fn = (V8Function) b.get(0);
			commander.register(
					fn,
					b.getString(1),
					b.getType(2) == V8Value.STRING ? b.getString(2) : null,
					b.getType(2) == V8Value.UNDEFINED
							? null : Arrays.asList(arr.getStrings(0, arr.length()))
			);
			arr.release();
		}, "addCommand");
		args.push(obj);
		v8Runtime.addUndefined("__server");
		v8Runtime.addUndefined("__helpers");

		final V8Object app = (V8Object) ((V8Function) nodeRuntime
				.require(new File(this.getDataFolder(), "setup-script.js")))
				.call(null, args);

		if (app.isUndefined()) {
			this.setEnabled(false);
			return false;
		}

		obj.release();
		args.release();
		this.app = app;
		return true;
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

	private Main checkModules () {
		if (!new File(System.getProperty("user.dir"), "node_modules/babel-polyfill")
				.isDirectory() && !Installer.install()) {
			this.getLogger().warning("Cannot to install all modules!");
		}
		return this;
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
