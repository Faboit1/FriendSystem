package com.faboit.friendsystem.api;

import java.util.Optional;

/**
 * Static access to the {@link FriendSystemAPI}.
 *
 * <p>The instance exists from the moment FriendSystem enables until it disables, so it
 * is safest to look it up when you need it rather than caching it in a field:</p>
 *
 * <pre>{@code
 * if (FriendSystemProvider.isAvailable() && FriendSystemProvider.get().areFriends(a, b)) {
 *     // ...
 * }
 * }</pre>
 *
 * <p>Plugins that declare {@code depend: [FriendSystem]} in their {@code plugin.yml} can
 * safely call {@link #get()} from {@code onEnable} onwards; plugins that use
 * {@code softdepend} should check {@link #isAvailable()} first.</p>
 */
public final class FriendSystemProvider {

    private static volatile FriendSystemAPI instance;

    private FriendSystemProvider() {
    }

    /**
     * The running API instance.
     *
     * @throws IllegalStateException if FriendSystem is not enabled
     */
    public static FriendSystemAPI get() {
        final FriendSystemAPI api = instance;
        if (api == null) {
            throw new IllegalStateException("FriendSystem is not enabled — add it to your plugin.yml "
                + "depend/softdepend list, or check FriendSystemProvider.isAvailable() first.");
        }
        return api;
    }

    /** The API instance, or empty when FriendSystem is not enabled. */
    public static Optional<FriendSystemAPI> find() {
        return Optional.ofNullable(instance);
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    /** Internal: called by FriendSystem itself. */
    public static void register(final FriendSystemAPI api) {
        instance = api;
    }

    /** Internal: called by FriendSystem itself. */
    public static void unregister() {
        instance = null;
    }
}
