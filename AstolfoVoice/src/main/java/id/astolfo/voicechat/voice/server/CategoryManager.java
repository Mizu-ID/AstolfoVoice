package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.voice.common.VolumeCategory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * CategoryManager — kategori volume yang dikirim ke client (AddCategory/RemoveCategory).
 */
public final class CategoryManager {

    private final ConcurrentHashMap<String, VolumeCategory> categories = new ConcurrentHashMap<>();

    public void addCategory(VolumeCategory category) {
        categories.put(category.getId(), category);
    }

    public void removeCategory(String id) {
        categories.remove(id);
    }

    public VolumeCategory getCategory(String id) {
        return categories.get(id);
    }

    public java.util.Collection<VolumeCategory> all() {
        return categories.values();
    }
}
