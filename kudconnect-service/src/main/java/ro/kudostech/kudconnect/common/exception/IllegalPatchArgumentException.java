package ro.kudostech.kudconnect.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class IllegalPatchArgumentException extends RuntimeException {
    private final Class<?> target;
}
