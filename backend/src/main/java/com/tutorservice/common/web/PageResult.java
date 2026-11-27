package com.tutorservice.common.web;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
public record PageResult<T>(List<T> items,String nextCursor,boolean hasMore) {
    public static UUID cursor(String value) {
        if(value==null) return new UUID(0,0);
        try {
            if(value.length()>64) throw ApiException.invalid();
            return UUID.fromString(new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8));
        } catch(IllegalArgumentException e) { throw ApiException.invalid(); }
    }
    public static int limit(int value) { if(value<1 || value>100) throw ApiException.invalid(); return value; }
    public static <E,T> PageResult<T> of(List<E> rows,int limit,Function<E,UUID> id,Function<E,T> view) {
        boolean more=rows.size()>limit; var items=rows.stream().limit(limit).toList();
        String cursor=more?Base64.getUrlEncoder().withoutPadding().encodeToString(id.apply(items.getLast()).toString().getBytes(StandardCharsets.UTF_8)):null;
        return new PageResult<>(items.stream().map(view).toList(),cursor,more);
    }
}
