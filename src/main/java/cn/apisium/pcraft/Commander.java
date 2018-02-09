package cn.apisium.pcraft;

import com.eclipsesource.v8.*;
import io.alicorn.v8.V8JavaAdapter;
import org.apache.commons.lang.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.defaults.BukkitCommand;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

final class Commander {
    private V8 v8;
    private SimpleCommandMap map;
    private final List<Command> list = new ArrayList<>();

    protected Commander (V8 v8, Main m) throws NoSuchFieldException, IllegalAccessException {
        this.v8 = v8;

        final Field method = m.getServer().getClass().getDeclaredField("commandMap");
        method.setAccessible(true);
        this.map = (SimpleCommandMap) method.get(m.getServer());
    }

    protected void register(V8Function callback, String name, String description, List<String> aliases) {
        final Command cmd = new Command(callback, name, description, aliases);
        map.register("pcraft", cmd);
        list.add(cmd);
    }

    protected void unRegisterAll() {
        for (Command cmd : list) {
            cmd.unregister(map);
        }
    }

    private class Command extends BukkitCommand {
        private V8Function callback;
        protected Command (V8Function callback, String name, String description, List<String> aliases) {
            super(name);
            this.callback = callback;
            this.description = description == null ? "" : description;
            this.usageMessage = "/" + name;
            if (aliases != null) this.setAliases(aliases);
        }

        @Override
        public boolean execute(CommandSender sender, String currentAlias, String[] commandArgs) {
            final V8Array args = new V8Array(v8);

            Object obj = new ObjectProxy(sender);
            final String id = "sender" + obj.hashCode();
            V8JavaAdapter.injectObject(id, obj, v8);
            V8Object o = v8.getObject(id);

            args.push(o)
                .push(currentAlias)
                .push(StringUtils.join(commandArgs, " "));
            v8.addUndefined(id);

            callback.call(null, args);
            o.release();
            args.release();
            return true;
        }
    }
}
