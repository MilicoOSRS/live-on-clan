package com.liveon;

import lombok.Getter;
import lombok.EqualsAndHashCode;

@Getter
@EqualsAndHashCode
final class MvpDropDetail
{
	private String item;
	private long value;
	private String source;
}
