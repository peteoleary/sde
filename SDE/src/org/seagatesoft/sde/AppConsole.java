package org.seagatesoft.sde;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Formatter;
import java.util.List;
import java.util.ArrayList;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.seagatesoft.sde.columnaligner.ColumnAligner;
import org.seagatesoft.sde.columnaligner.PartialTreeAligner;
import org.seagatesoft.sde.datarecordsfinder.DataRecordsFinder;
import org.seagatesoft.sde.datarecordsfinder.MiningDataRecords;
import org.seagatesoft.sde.dataregionsfinder.DataRegionsFinder;
import org.seagatesoft.sde.dataregionsfinder.MiningDataRegions;
import org.seagatesoft.sde.tagtreebuilder.DOMParserTagTreeBuilder;
import org.seagatesoft.sde.tagtreebuilder.TagTreeBuilder;
import org.seagatesoft.sde.treematcher.EnhancedSimpleTreeMatching;
import org.seagatesoft.sde.treematcher.SimpleTreeMatching;
import org.seagatesoft.sde.treematcher.TreeMatcher;
import org.xml.sax.SAXException;

/**
 * Aplikasi utama yang berbasis konsol.
 *
 * @author seagate
 */
public class AppConsole
  {
  private static Logger logger = LoggerFactory.getLogger( AppConsole.class );

  public static String GetArgumentOrDefault( String args[], int index, String def )
    {
    return index >= args.length ? def : args[ index ];
    }

  public static String MakeDefaultOutput(String input) throws MalformedURLException
    {
    URL aURL = new URL(input);

    ArrayList<String> parts = Lists.newArrayList(Splitter.on('/').trimResults().omitEmptyStrings().split( aURL.getPath() ));

    parts.add( 0, new SimpleDateFormat( "yyyyMMdd" ).format( Calendar.getInstance().getTime() ) );
    parts.add( 1,  aURL.getHost());

    // side effect: creates directories
    new File(Joiner.on('/').join( parts.subList( 0, parts.size() - 1 ) )).mkdirs();

    return Joiner.on('/').join(parts);
  }

  /**
   * Method main untuk aplikasi utama yang berbasis konsol. Ada empat argumen yang bisa diberikan,
   * urutannya URI file input, URI file output, similarity treshold, jumlah node maksimum dalam generalized node.
   *
   * @param args Parameter yang dimasukkan pengguna aplikasi konsol
   */
  public static void main( String args[] )
    {
    if( args.length == 0 )
      {
      System.err.println( "Please specify the extraction URI..." );
      System.exit( 1 );
      }
    int parameter_index = 0;

    try {

    // TODO: implement named parameters here
    // parameter default
    String input = GetArgumentOrDefault( args, parameter_index++, "" );
    String resultOutput = GetArgumentOrDefault( args, parameter_index++, MakeDefaultOutput(input) );
    double similarityTreshold = Double.parseDouble( GetArgumentOrDefault( args, parameter_index++, "0.9" ) );
    boolean ignoreFormattingTags = Boolean.parseBoolean( GetArgumentOrDefault( args, parameter_index++, "false" ) );
    boolean useContentSimilarity = Boolean.parseBoolean( GetArgumentOrDefault( args, parameter_index++, "false" ) );
    int maxNodeInGeneralizedNodes = Integer.parseInt( GetArgumentOrDefault( args, parameter_index++, "9" ) );

    // parameter dari pengguna, urutannya URI file input, URI file output, similarity treshold, jumlah node maksimum dalam generalized node
    // parameter yang wajib adalah parameter URI file input

    // TODO: factor out input handler so that it can be used multiple times

    logger.info( String.format( "input=%s, resultOutput=%s, similarityTreshold=%f, ignoreFormattingTags=%b, useContentSimilarity=%b, maxNodeInGeneralizedNodes=%d", input, resultOutput, similarityTreshold, ignoreFormattingTags, useContentSimilarity, maxNodeInGeneralizedNodes ) );

    List<String[][]> dataTables = SDEOperation.operate(input, similarityTreshold, ignoreFormattingTags, useContentSimilarity, maxNodeInGeneralizedNodes);

    JSONArray allTables = SDEOperation.output(dataTables, String.format( "%s.html", resultOutput ), null);

    FileWriter file = new FileWriter( String.format( "%s.json", resultOutput ) );
    file.write( allTables.toString() );
    file.flush();
    file.close();

    }
  catch( SecurityException exception )
    {
    exception.printStackTrace();
    System.exit( 1 );
    }
  catch( FileNotFoundException exception )
    {
    exception.printStackTrace();
    System.exit( 2 );
    }
  catch( IOException exception )
    {
    exception.printStackTrace();
    System.exit( 3 );
    }
  catch( SAXException exception )
    {
    exception.printStackTrace();
    System.exit( 4 );
    }
  catch( Exception exception )
    {
    exception.printStackTrace();
    System.exit( 5 );
    }
  }


  }