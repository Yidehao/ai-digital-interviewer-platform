package org.interviewer.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * JSON conversion utility class.
 *
 * Note the failure contract: every method returns null on error rather than throwing. Callers
 * that must not silently lose data (session persistence, anything that round-trips through
 * Redis) should use the Spring-managed ObjectMapper instead.
 */
public class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    // Registers JavaTimeModule: without it every LocalDate/LocalDateTime field on the entities
    // and VOs fails to serialise, which surfaces as a mysterious null rather than an error.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Convert object to JSON string
     * @param data
     * @return the JSON string, or null if serialisation failed
     */
    public static String objectToJson(Object data) {
    	try {
			return MAPPER.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialise {} to JSON", data == null ? "null" : data.getClass().getName(), e);
		}
    	return null;
    }

    /**
     * Convert JSON result set to object
     *
     * @param jsonData JSON data
     * @param beanType Object type in the object
     * @return the parsed object, or null if parsing failed
     */
    public static <T> T jsonToPojo(String jsonData, Class<T> beanType) {
        try {
            return MAPPER.readValue(jsonData, beanType);
        } catch (Exception e) {
        	log.error("Failed to parse JSON into {}", beanType.getName(), e);
        }
        return null;
    }

    /**
     * Convert JSON data to POJO object list
     * @param jsonData
     * @param beanType
     * @return the parsed list, or null if parsing failed
     */
    public static <T>List<T> jsonToList(String jsonData, Class<T> beanType) {
    	JavaType javaType = MAPPER.getTypeFactory().constructParametricType(List.class, beanType);
    	try {
    		return MAPPER.readValue(jsonData, javaType);
		} catch (Exception e) {
			log.error("Failed to parse JSON into List<{}>", beanType.getName(), e);
		}

    	return null;
    }

}
