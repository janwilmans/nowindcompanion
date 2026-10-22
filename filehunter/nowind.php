<?php
	/* Serverside File Download handler for the MSX-PICO WiFi module v1.15 - Written by Jaap@bugreport.nl
	 *
	 * Usage:
	 * 		All parameters are Url-parameters
	 *
	 *		[optional] 	type:   	Filetype to display or download. One of [ CAS, DSK, ROM, VGM ]. Not case-sensitive. Defaults to ROM for compatibility reasons.
	 *		[optional]  msx: 		One of [ 1, 2, 2+, Turbo-R ] or empty for 'all MSX types'. 
	 *						 		Invalid values or not providing the parameter will default to 'ALL'. Not case-sensitive.
	 *						 
	 *		[optional]  char: 		Either one character long alphanumeric [A-z, 0-9]                 --> Only show ROMs starting with this character in the name.
	 *						 		Or more than one character long (and not limited to alphanumeric) --> Only show ROMs containing these characters in the name.
	 *						 		Invalid values or not providing the parameter will default to 'ALL'. Not case-sensitive. 
	 * 
	 *                          
     *		[optional]  download:	Numeric index of the file to download (accounting for the optionally provided type, char and type parameters).
	 *								0 based. Used to actually download a file. Invalid values or not providing the parameter will default to showing the listing instead.
	 *								For a download the HTTP body response will have 1 line of meta-data, followed by \n, followed by the raw file data.
	 *								For example: type:normal,start:0x4000,size:32768,name:test\n<file bytes>
	 *								
	 *									type 	describes the ROM-mapper typo of the ROM                      - if provided in the Vampier RomDB - or empty. For ROM files only.
	 *									start 	the hexadecimal startaddress of the ROM in the format: 0x1234 - if provided in the Vampier RomDB - or empty. For ROM files only.
	 *									size	the (decimal) size of the file in bytes
	 *									name	the filename being downloaded in displayname format, not the raw filepath.
	 *
	 *
	 * Usage Examples:
	 *		index.php											List ROM files (= default type) for all MSX types followed by "\t" and the filesize in bytes.
	 *		index.php?msx=2+									List MSX2+ ROM files followed by "\t" and the filesize in bytes.
	 *		index.php?type=DSK									List DSK files followed by "\t" and the filesize in bytes.
	 *		index.php?char=A									List ROM files starting with the character 'A' or 'a' followed by "\t" and the filesize in bytes.
	 *		index.php?msx=1&char=B								List MSX1 ROMs starting with the character 'B' or 'b' followed by "\t" and the filesize in bytes.
	 *		index.php?char=Konami								List ROMs containing 'Konami' (non case-sensitive) in their name; like a search command, followed by "\t" and the filesize in bytes.
	 *		index.php?msx=2&char=Metal+Gear						List MSX2 ROM files containing 'Metal Gear' (non case-sensitive) in their name; like a search command, followed by "\t" and the filesize in bytes.
	 *		index.php?msx=2&char=Konami&type=DSK				List MSX2 DSK files containing 'Konami' (non case-sensitive) in their name; like a search command, followed by "\t" and the filesize in bytes.
	 *		index.php?download=0								Download the 1st ROM in the list as shown when calling index.php?
	 *		index.php?type=DSK&download=0						Download the 1st ROM in the list as shown when calling index.php?type=DSK
	 *		index.php?msx=2+&download=0							Download the 1st ROM in the list as shown when calling index.php?msx=2+
	 *		index.php?msx=2&char=Metal+Gear&download=1			Download the 2nd ROM in the list as shown when calling index.php?msx=2&char=Metal+Gear
	 *		index.php?msx=2&char=Konami&type=DSK&download=1		Download the 2nd ROM in the list as shown when calling index.php?msx=2&char=Konami
	 *
	 *
	 * Installation:
	 *		Requires a softwaredb.xml taken from https://romdb.vampier.net/Archive//softwaredb-latest.zip to be put in the same directory as this script.
	 *		Requires a text-file describing all the available roms in 'allfiles.txt' put in the same directory as this script.
	 *		Requires the downloadble files to be available as .zip files on the relative file-path from this script as described in 'allfiles.txt'. 
	 *      Requires the PHP ZIP extension to be enabled.
	 *		Requires subfolders within the starting subfolder GAMES named MSX1, MSX2, MSX2+ and MSX Turbo-R to have the 'type' parameter work correctly, the starting subfolder can be adjusted on line 82.       
	 *		Requires the prep_allfiles.php script to pre-process the 'allfiles.txt' first for sorting, filtering and name-substitution first.
	 */

	// For debugging/troubleshooting only !
	error_reporting(E_ALL);
	ini_set('display_errors', 1);

	// Optionally: Suppress caching
	header("Expires: 0");
	header("Cache-Control: no-store, no-cache, must-revalidate");
	
	// Read UrlParams or use defaults
	$msxGen   		= isset($_GET["msx"])		? strtolower(urlencode($_GET["msx"])) 	: ""; // Please note the UrlEncode instead of Decode to make the plus in 'msx2+' be a real plus-sign and not a whitespace.
	$firstChar 		= isset($_GET["char"])		? urldecode($_GET["char"])  			: "";
	$fileType  		= isset($_GET["type"]) 		? strtolower($_GET["type"]) 			: "rom";
	$downloadIndex  = isset($_GET["download"])  ? $_GET["download"] 					: -1;
		
	// type must be either [ MSX1, MSX2, MSX2+, Turbo-R ] or it will default to ".+" for 'All types'.
	switch ($msxGen) {
		case "1": 		$msxGen = "msx1";		break;
		case "2": 		$msxGen = "msx2";		break;
		case "2+": 		$msxGen = "msx2\+"; 	break;
		case "turbo-r": $msxGen = " turbo-r";	break;
		default: 		$msxGen = ".+"; 		// Match all types
	}
	
	// If firstChar is not of length 1 and not alphanumeric, make it match '*<searchterm>*' instead, escaping (most) RegExp special characters.
	if (strlen($firstChar) != 1 || !ctype_alnum($firstChar)) {
		$firstChar = ".*".str_replace("{", "\{", str_replace("}", "\}", str_replace("[", "\[", str_replace("]", "\]", 
						  str_replace("(", "\(", str_replace(")", "\)", str_replace("+", "\+", str_replace("*", "\*",
						  str_replace("!", "\!", str_replace("^", "\^", str_replace("?", "\?", str_replace(".", "\.", 
						  str_replace("<", "\<", str_replace(">", "\>", str_replace("%", "\%", str_replace("|", "\|", 
						  str_replace("&", "\&", str_replace("=", "\=", str_replace("/", "\/", str_replace("\\", "\\\\", 
						  $firstChar)))))))))))))))))))).".";
	}

	// Read allfiles.txt, stripping '\r' from the '\r\n' newline and filter the array list on the file-type and search-pattern
	$all   = str_replace("\r", "", file(__DIR__."/allfiles.txt"));
	if ($fileType === "vgm") {
		$files = array_values(preg_grep("/\|\|Music\/VGM\/+.*/i", $all));
	} else {
		$files = array_values(preg_grep("/\|\|Games\/".$msxGen."\/".$fileType."\/+.*/i", $all));
	}
	$files = array_values(preg_grep("/\|\|.+\/...\/".$firstChar."+.*\|\|/i", $files));
	
	//Do we start a download ?
	if (is_numeric($downloadIndex) && $downloadIndex >= 0 && $downloadIndex < count($files)) {
		$game = "/".explode("||", $files[$downloadIndex])[1];
		$content = getZipFileContent($game, $fileType);
		
		// Handle each filetype properly
		switch ($fileType) {
			case "dsk":
				$gameSet = [];
				if (getGameSetFromName($game, $files, $gameSet))  {
					$content = "";
					for ($i=0; $i < count($gameSet); $i++)
						$content = $content.getZipFileContent("/".explode("||", $gameSet[$i])[1], $fileType);
				} else {
					$gameSet = [ $game ];
				}
				
				// Try and remove hardcoded POKE -1,170 and POKE -1,255 with and without a whitespace after POKE and after ,
				$content = preg_replace("/\x98\xF2\x12\x2C\x0F[\xAA\xFF]/"			, "\x98\xF2\x11\x2C\x0F\xF3"		, $content);
				$content = preg_replace("/\x98\x20\xF2\x12\x2C\x0F[\xAA\xFF]/"		, "\x98\x20\xF2\x11\x2C\x0F\xF3"	, $content);
				$content = preg_replace("/\x98\x20\xF2\x12\x2C\x0F\x20[\xAA\xFF]/"	, "\x98\x20\xF2\x11\x2C\x0F\x20\xF3", $content);
				$header = "size:".strlen($content).",disks:".count($gameSet).",name:".explode("(Disk ", basename($game))[0].".".$fileType;
				break;
		
			case "vgm":
				$header = "size:".strlen($content).",name:".basename($game).".".$fileType;
				break;

			case "rom":
				$sha1  = sha1($content);
				$romDB = file(__DIR__."/softwaredb.xml");
				$meta  = array_values(preg_grep("/.\<hash\>".$sha1."\<\/hash\>*/", $romDB));

				// Build metadata info
				if (count($meta) == 1) {
					$xml    = (array) new SimpleXMLElement($meta[0]);
					$xml    = (array) (isset($xml["megarom"]) ? $xml["megarom"] : $xml["rom"]);
					$type   = isset($xml["type"])  ? strtolower($xml["type"])   : "";
					$start  = isset($xml["start"]) ? strtolower($xml["start"])  : ""; 			// In 99% of the cases this value is empty, which implies: 0x4000. 
					$header = "type:".$type.",start:".$start.",size:".strlen($content).",name:".basename($game).".rom"; 
				} else {
					$header = "type:,start:,size:".strlen($content).",name:".basename($game).".rom"; 
				}
				break;
				
			default:
				// Should never happen
				$header = "You broke it !";
		}
		
		#header("Content-type: application/octet-stream"); echo $content; // For troubleshooting
		echo $header."\n".$content;
		
	} else {

		if (count($files) >= 1) {
			// No (valid) download, just show the listing
			foreach($files as $file) {
				$info = explode("||", $file);
				echo $info[0]."\t".$info[2];
			}
		} else {
			echo "No files found\t0\n";
		}
	}


	# Filenames within the ZIP archive are case-sensitive and do not always match the .ZIP filename exactly.
	# Also: get_file_content("://zip") cannot handle "#" in the filename. 
	# Therefor we use ZipArchive and we just return the first <$fileType> file in the .ZIP file.
	function getZipFileContent($fileName, $fileType) {
		$content = null;
		$zip     = new ZipArchive();
		$zip->open(__DIR__.$fileName.".zip");
		for ($i=0; $i < $zip->numFiles; $i++) {
			if (str_ends_with(strtolower($zip->statIndex($i)["name"]), ".".$fileType)) {
				$content = $zip->getFromName($zip->statIndex($i)["name"]);
				break;
			}
		}
		$zip->close();
		return $content;

	}
	
function getGameSetFromName($input, array $files, &$results = []) {
    // Validate input
    if (!is_string($input) || empty(trim($input))) return false;

    // Extract (Disk x of y)
    $diskPattern = '/\(Disk\s*(\d+)\s*of\s*(\d+)\)$/i';
    if (!preg_match($diskPattern, $input, $diskMatches)) return false;

    $x = $diskMatches[1]; // e.g., "2"
    $y = $diskMatches[2]; // e.g., "2"

    // Extract name by removing path and (Disk x of y)
    $name = preg_replace($diskPattern, '', $input);

    // Remove path
    $name = preg_replace('/^.*\//', '', $name);

    // Remove trailing meta groups (e.g., (1989)(Data West)(ja))
    $namePattern = '/^(.+?)(?:\s*\([^)]+\))*$/i';
    if (!preg_match($namePattern, $name, $nameMatches)) return false;

    $name = trim($nameMatches[1]);

    // Escape special characters
    $escapedName = preg_quote($name, '/');
    $escapedY = preg_quote($y, '/');

    // Regex to match: "Some name [optional meta](Disk x of y)"
    $searchPattern = '/^' . $escapedName . '(?:\s*\([^)]+\))*\s*\(Disk\s*(\d+)\s*of\s*' . $escapedY . '\)$/i';

    // Search $files
    $found = false;
    foreach ($files as $index => $file) {

        // Extract filename from ||-delimited string
        $fileParts = explode('||', $file);
        $filename  = trim($fileParts[0]);

        // Test regex match
        if (preg_match($searchPattern, $filename, $matches)) {
            $results[] = $file;
            $found = true;
        }
    }

    return $found;
}
?>
