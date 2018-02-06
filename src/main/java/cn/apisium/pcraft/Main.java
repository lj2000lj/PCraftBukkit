package cn.apisium.pcraft;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import javax.xml.ws.Holder;

import com.eclipsesource.v8.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import io.alicorn.v8.V8JavaAdapter;

final public class Main extends JavaPlugin implements Listener {
	private V8 v8Runtime = null;
	private NodeJS nodeRuntime = null;
	private V8Object app = null;
	private final RegisteredListener registeredListener = new RegisteredListener(
			this,
			(listen, event) -> Main.this.onFire(event),
			EventPriority.MONITOR,
			this,
			false
	);

	@Override
	public void onLoad() {
		nodeRuntime = NodeJS.createNodeJS();
		v8Runtime = nodeRuntime.getRuntime();
	}

	@Override
	public void onDisable() {
		for (HandlerList handler : HandlerList.getHandlerLists()) {
			handler.unregister(registeredListener);
		}

		if (app != null) {
			final V8Array args = new V8Array(this.v8Runtime);
			final CountDownLatch latch = new CountDownLatch(1);
			final Holder<Boolean> notified = new Holder<>(false);

			V8Function cb = new V8Function(v8Runtime, (a, b) -> {
				try { latch.countDown(); } catch (Throwable ignored) { }
				notified.value = true;
				return null;
			});
			args.push(cb);

			app.executeVoidFunction("disable", args);
			try {
				latch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			cb.release();
			args.release();

			app.release();
		}
		this.getServer().getScheduler().cancelTasks(this);
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
		this.app = app;

		for (HandlerList handler : HandlerList.getHandlerLists()) {
			handler.register(registeredListener);
		}
		while (nodeRuntime.isRunning()) nodeRuntime.handleMessage();
		this.getLogger().info("Loaded successful!");
	}

	private void onFire(Event event) {
		final V8Array args = new V8Array(this.v8Runtime);

		final CountDownLatch latch = new CountDownLatch(1);
		final Holder<Boolean> notified = new Holder<>(false);

		final String id = "event_" + event.hashCode();
		V8JavaAdapter.injectObject(id, event, v8Runtime);

		V8Function cb = new V8Function(v8Runtime, (a, b) -> {
			try { latch.countDown(); } catch (Throwable ignored) { }
			notified.value = true;
			return null;
		});
		args.push(v8Runtime.getObject(id)).push(cb);

		v8Runtime.addUndefined(id);

		app.executeVoidFunction("emit", args);
		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		cb.release();
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
				final byte[] bytes = new byte[in.available()];
				in.read(bytes);
				Files.write(pkg, bytes);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
}
