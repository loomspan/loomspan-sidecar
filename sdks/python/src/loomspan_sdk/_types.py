from decimal import Decimal


type JsonValue = None | bool | int | float | Decimal | str | list[JsonValue] | JsonObject
type JsonObject = dict[str, JsonValue]
