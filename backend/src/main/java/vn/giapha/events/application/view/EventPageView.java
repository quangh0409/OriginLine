package vn.giapha.events.application.view;

import java.util.List;

/** Trang danh sách sự kiện. */
public record EventPageView(List<EventView> items, PageMetaView page) {
}
