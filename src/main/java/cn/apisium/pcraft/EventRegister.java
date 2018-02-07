package cn.apisium.pcraft;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final public class EventRegister {
    private String[] packageNames = new String[] {
            "org.bukkit.event.block.",
            "org.bukkit.event.enchantment.",
            "org.bukkit.event.entity.",
            "org.bukkit.event.hanging.",
            "org.bukkit.event.inventory.",
            "org.bukkit.event.player.",
            "org.bukkit.event.server.",
            "org.bukkit.event.vehicle.",
            "org.bukkit.event.weather.",
            "org.bukkit.event.world."
    };
    private List<HandlerList> registered = new ArrayList<>();
    private List<String> registeredList = new ArrayList<>();
    private RegisteredListener handler;

    protected EventRegister (RegisteredListener handler) {
        this.handler = handler;
    }

    protected void unRegisterAll() {
        for (HandlerList h : registered) {
            h.unregister(handler);
        }
    }

    protected void register(String name) throws IllegalPluginAccessException, NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        if (name == null || name.equals("")) {
            throw new IllegalPluginAccessException("Woring EventName!");
        }
        if (registeredList.contains(name)) return;
        Method method = find(name).getDeclaredMethod("getHandlerList", new Class[0]);
        method.setAccessible(true);
        HandlerList h = ((HandlerList) method.invoke(null, new Object[0]));
        h.register(handler);
        registered.add(h);
        registeredList.add(name);
    }

    private Class<? extends Event> find(String name) {
        Class<? extends Event> clazz = null;
        for (String pkgName : packageNames) {
            try {
                clazz = (Class<? extends Event>) Class.forName(pkgName + name + "Event");
            } catch (Exception ignored) { }
        }
        if (clazz == null) throw new IllegalPluginAccessException("No such Event: " + name);
        return getRegistrationClass(clazz);
    }

    private Class<? extends Event> getRegistrationClass(Class<? extends Event> clazz)
    {
        try {
            clazz.getDeclaredMethod("getHandlerList", new Class[0]);
            return clazz;
        } catch (NoSuchMethodException localNoSuchMethodException) {
            if ((clazz.getSuperclass() != null) &&
                    (!clazz.getSuperclass().equals(Event.class)) &&
                    (Event.class.isAssignableFrom(clazz.getSuperclass()))) {
                return getRegistrationClass(clazz.getSuperclass().asSubclass(Event.class));
            }
            throw new IllegalPluginAccessException("Unable to find handler list for event " +
                    clazz.getName() + ". Static getHandlerList method required!");
        }
    }
}
