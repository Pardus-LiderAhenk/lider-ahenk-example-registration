package tr.org.liderahenk.registration.subscriber;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CsvReader {

	public Map<String, String[]> read(String filePath) {

		Map<String, String[]> recordsMap = new HashMap<String, String[]>();

		BufferedReader br = null;
		String cvsSplitBy = ",";
		String line = "";

		try {
			br = new BufferedReader(new FileReader(filePath));
			while ((line = br.readLine()) != null) {

				String[] record = line.split(cvsSplitBy);
				if (record.length > 0) {
					String[] dcParameters = Arrays.copyOfRange(record, 1, record.length);
					recordsMap.put(record[0], dcParameters);
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return recordsMap;
	}

}
