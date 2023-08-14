package ai.kiya.process.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class BaseResponseVO {
	private String message;
	private String errorMessage;
}
