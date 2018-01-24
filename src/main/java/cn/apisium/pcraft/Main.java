package cn.apisium.pcraft;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

import javax.xml.ws.Holder;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.NodeJS;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;

import cn.apisium.pcraft.variable.NonConfig;
import cn.apisium.pcraft.variable.Variables;
import io.alicorn.v8.V8JavaAdapter;

public class Main extends JavaPlugin implements Listener {
	NodeJS nodeRuntime = null;
	V8 v8Runtime = null;
	RegisteredListener registeredListener = new RegisteredListener(this, new EventExecutor() {
		@Override
		public void execute(Listener p0, Event p1) throws EventException {
			Main.this.onFire(p1);
		}
	}, EventPriority.MONITOR, this, false);

	public void onLoad() {
		nodeRuntime = NodeJS.createNodeJS();
		v8Runtime = nodeRuntime.getRuntime();
	}

	@Override
	public void onDisable() {
		for (HandlerList handler : HandlerList.getHandlerLists()) {
			handler.unregister(registeredListener);
		}
	}

	@Override
	public void onEnable() {
		this.loadConfig().getServer().getPluginManager().registerEvents(this, this);

		this.insert("server", Bukkit.getServer(), v8Runtime);

		this.registerClass(Bukkit.class);
		this.registerClass(PCraftHelper.class);

		v8Runtime.executeScript(
				"emit = (event, callback) => event.getEventName() === 'PlayerJoinEvent' ? setTimeout(callback, 200000) : callback()");
	}

	public void onFire(Event event) {
		V8Array args = new V8Array(this.v8Runtime);
		
		this.insert("__event_" + event.hashCode(), event, v8Runtime);
		
		args.push(v8Runtime.getObject("__event_" + event.hashCode()));
		
		final CountDownLatch latch = new CountDownLatch(1);
		long t = System.nanoTime();
		final Holder<Boolean> notifyed = new Holder<Boolean>(false);
		args.registerJavaMethod(new JavaVoidCallback() {

			@Override
			public void invoke(V8Object receiver, V8Array parameters) {
				try {
					latch.countDown();
				} catch (Throwable e) {
				}
				notifyed.value = true;

			}
		}, "__notify");
		args.push(args.getObject("__notify"));
		v8Runtime.executeFunction("emit", args);
		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		getLogger().info("" + (System.nanoTime() - t));
	}

	public Main registerListener() {
		for (HandlerList handler : HandlerList.getHandlerLists()) {
			handler.register(registeredListener);
		}
		return this;
	}

	public Main registerClass(final Class<?> clazz) {
		V8JavaAdapter.injectClass(clazz, v8Runtime);
		return this;
	}

	public Main insert(String name, final Object obj, V8Object v8) {
		V8JavaAdapter.injectObject(name, obj, v8);
		return this;
	}

	public Main loadConfig() {
		if (!this.getDataFolder().exists()) {
			this.getDataFolder().mkdirs();
		}
		this.saveDefaultConfig();
		Class<?> variables = Variables.class;
		for (Field variable : variables.getDeclaredFields()) {
			if (variable.isAnnotationPresent(NonConfig.class)) {
				continue;
			}
			String path = capitalFirst(variable.getName());
			try {
				Object defaultValue = variable.get(null);
				Object config = this.getConfig().get(path, null);
				if (config != null)
					variable.set(null, config);
				else if (defaultValue != null)
					this.getConfig().set(path, defaultValue);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		this.saveConfig();
		return this;
	}

	public String capitalFirst(String string) {
		char[] cs = string.toCharArray();
		if (Character.isLowerCase(cs[0])) {
			cs[0] = Character.toUpperCase(cs[0]);
			return String.valueOf(cs);
		}
		return string;
	}
}
