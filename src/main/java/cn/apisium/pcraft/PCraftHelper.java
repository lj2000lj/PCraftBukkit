package cn.apisium.pcraft;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

final class PCraftHelper {
	public Class<?> getClassFromName(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	public RegisteredServiceProvider<?> getRegistration(String name) {
		return Bukkit.getServer().getServicesManager().getRegistration(getClassFromName(name));
	}
}
