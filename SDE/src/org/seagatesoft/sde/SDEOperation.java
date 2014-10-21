/*
 * Copyright (c) 2007-2014 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.cascading.org/
 *
 * This file is part of the Cascading project.
 */

package org.seagatesoft.sde;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONArray;
import org.json.JSONException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 */
public class SDEOperation
  {
  private static Logger logger = LoggerFactory.getLogger( AppConsole.class );

  public static List<String[][]> operate( String input, double similarityTreshold, boolean ignoreFormattingTags,
                                          boolean useContentSimilarity, int maxNodeInGeneralizedNodes ) throws IOException, SAXException
    {
    // buat objek TagTreeBuilder yang berbasis parser DOM
    TagTreeBuilder builder = new DOMParserTagTreeBuilder();

    // bangun pohon tag dari file input menggunakan objek TagTreeBuilder yang telah dibuat
    TagTree tagTree = builder.buildTagTree( input, ignoreFormattingTags );

    tagTree.Summarize();
    // prints out the complete HTML page to the output file path, don't do this...
    // printHTML( tagTree.getRoot());

    // buat objek TreeMatcher yang menggunakan algoritma simple tree matching
    TreeMatcher matcher = new SimpleTreeMatching();

    // buat objek DataRegionsFinder yang menggunakan algoritma mining data regions dan
    // menggunakan algoritma pencocokan pohon yang telah dibuat sebelumnya
    DataRegionsFinder dataRegionsFinder = new MiningDataRegions( matcher );

    // cari data region pada pohon tag menggunakan objek DataRegionsFinder yang telah dibuat
    List<DataRegion> dataRegions = dataRegionsFinder.findDataRegions( tagTree.getRoot(), 0, maxNodeInGeneralizedNodes, similarityTreshold );

    logger.info( String.format( "dataRegions found=%d", dataRegions.size() ) );

    // buat objek DataRecordsFinder yang menggunakan metode mining data records dan
    // menggunakan algoritma pencocokan pohon yang telah dibuat sebelumnya
    DataRecordsFinder dataRecordsFinder = new MiningDataRecords( matcher );

    // buat matriks DataRecord untuk menyimpan data record yang teridentifikasi oleh
    // DataRecordsFinder dari List<DataRegion> yang ditemukan
    DataRecord[][] dataRecords = new DataRecord[ dataRegions.size() ][];

    // identifikasi data records untuk tiap2 data region
    for( int dataRecordArrayCounter = 0; dataRecordArrayCounter < dataRegions.size(); dataRecordArrayCounter++ )
      {
      DataRegion dataRegion = dataRegions.get( dataRecordArrayCounter );
      dataRecords[ dataRecordArrayCounter ] = dataRecordsFinder.findDataRecords( dataRegion, similarityTreshold );
      }

    // buat objek ColumnAligner yang menggunakan algoritma partial tree alignment
    ColumnAligner aligner = null;
    if( useContentSimilarity )
      {
      aligner = new PartialTreeAligner( new EnhancedSimpleTreeMatching() );
      }
    else
      {
      aligner = new PartialTreeAligner( matcher );
      }
    List<String[][]> dataTables = new ArrayList<String[][]>();

    // bagi tiap2 data records ke dalam kolom sehingga berbentuk tabel
    // dan buang tabel yang null
    for( int tableCounter = 0; tableCounter < dataRecords.length; tableCounter++ )
      {
      String[][] dataTable = aligner.alignDataRecords( dataRecords[ tableCounter ] );

      if( dataTable != null )
        {
        dataTables.add( dataTable );
        }
      }

    return dataTables;
    }

  public static JSONArray output( List<String[][]> dataTables, String outputPath, JSONArray previousTable ) throws JSONException, FileNotFoundException
    {
    Formatter output = new Formatter( outputPath );
    JSONArray result = output( dataTables, output, previousTable );
    output.flush();
    output.close();
    return result;
    }

  public static JSONArray output( List<String[][]> dataTables, Formatter output, JSONArray previousTable ) throws JSONException
    {
    JSONArray allTables = new JSONArray();

    // write extracted data to output file
    // TODO: factor output code, move DescriptiveStatistics  as well
    output.format( "<html><head><title>Extraction Result</title>" );
    output.format( "<style type=\"text/css\">table {border-collapse: collapse;} td {padding: 5px} table, th, td { border: 1px solid gray;} td.same { border: 2px solid green} td.close { border: 2px solid orange} </style>" );
    output.format( "</head><body>" );
    int tableCounter = 0;
    for( String[][] table : dataTables )
      {
      JSONArray oneTable = new JSONArray();

      ArrayList<DescriptiveStatistics> columnStats = null;

      output.format( "<h2>Table %s</h2>\n<table>\n<thead>\n<tr>\n<th>Row Number</th>\n", tableCounter + 1 );
      for( int columnCounter = 0; columnCounter < table[ 0 ].length; columnCounter++ )
        {
        output.format( "<th>%s</th>\n", columnStats != null && columnCounter < columnStats.size() ? String.format( "%.2f/%.2f", columnStats.get( columnCounter ).getMean(), columnStats.get( columnCounter ).getStandardDeviation() ) : Integer.toString( columnCounter ) );
        }
      output.format( "</tr>\n</thead>\n<tbody>\n" );

      logger.info( String.format( "table height= %d width=%d", dataTables.get( tableCounter ).length, dataTables.get( tableCounter )[ 0 ].length ) );

      int rowCounter = 0;
      for( String[] row : table )
        {
        JSONArray previousRow = null;
        if( previousTable != null )
          {
          previousRow = (JSONArray) previousTable.get( rowCounter );
          }
        JSONArray oneRow = new JSONArray();
        output.format( "<tr>\n<td>%s</td>", rowCounter + 1 );
        int columnCounter = 0;
        for( String item : row )
          {
          String cellClass = "";
          String itemText = item;
          if( itemText == null )
            {
            itemText = "";
            }
          if( previousRow != null )
            {
            if( columnCounter < previousRow.length() )
              {
              DescriptiveStatistics col = columnStats.get( columnCounter );
              if( previousRow.getInt( columnCounter ) == itemText.length() )
                {
                cellClass = "same";
                }
              else if( col.getStandardDeviation() > 3 )
                {
                cellClass = "close";
                }
              }
            }
          output.format( "<td class='%s'>%s</td>\n", cellClass, itemText );
          oneRow.put( itemText.length() );
          columnCounter++;
          }
        output.format( "</tr>\n" );
        rowCounter++;
        logger.info( String.format( "%s", oneRow.toString() ) );

        oneTable.put( oneRow );
        }
      output.format( "</tbody>\n</table>\n" );
      tableCounter++;

      allTables.put( oneTable );
      }

    output.format( "</body></html>" );

    return allTables;
    }

    private static ArrayList<DescriptiveStatistics> ComputeDescriptiveStatistics ( JSONArray previousTable)throws
    JSONException
    {
    ArrayList<DescriptiveStatistics> averages = new ArrayList<DescriptiveStatistics>();
    int height = previousTable.length(), width = ( (JSONArray) previousTable.get( 0 ) ).length();

    for( int i = 0; i < width; i++ )
      {
      DescriptiveStatistics stats = new DescriptiveStatistics();
      for( int j = 0; j < height; j++ )
        {
        stats.addValue( ( (JSONArray) ( (JSONArray) previousTable ).get( j ) ).getInt( i ) );
        }
      averages.add( stats );
      }

    return averages;
    }
    }
