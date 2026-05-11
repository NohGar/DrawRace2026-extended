package backend.drawrace.global.rsdata;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RsData<T> {
    private final String resultCode;
    private final String msg;
    private final T data;

    public RsData(String resultCode, String msg) {
        this(resultCode, msg, null);
    }

    @JsonIgnore
    public int statusCode() {
        return Integer.parseInt(resultCode.split("-")[0]);
    }
}
