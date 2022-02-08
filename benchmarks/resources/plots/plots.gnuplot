## Generates plots for performance documentation.
## TODO: Automate this somehow.
## run `gnuplot plots.gnuplot` to generate png files.

set terminal png
set grid ytics xtics
set grid
set auto x
set style data histogram
set style histogram cluster gap 1
set style fill solid border -1
set boxwidth 0.9
set xtic scale 0
set yrange [0:]
set style fill solid 0.5
set key opaque

#--------------------------------------------------------------------#

$data << EOD
Title "proto map with hinted p->" "Java POJO"
"5" 284576608 315359072     
"10" 295413108 325435049  
"20" 281711691 320019201 
EOD


set output "read_hinted_tp.png"
set ylabel "Throughput (ops/sec)"
set xlabel "Schema size"

plot "$data" \
    using 2:xtic(1) title col, '' using 3:xtic(1) title col


#--------------------------------------------------------------------#

$data << EOD
Title "clojure map" "proto map" "Java POJO"
"5" 148435363 142588048 315359072     
"10" 57601983 92661824 325435049  
"20" 44475967 103589838 320019201 
EOD

set output "read_tp.png"
set ylabel "Throughput (ops/sec)"
set xlabel "Schema size"

plot "$data" \
    using 2:xtic(1) title col, '' using 3:xtic(1) title col, '' using 4:xtic(1) title col

#--------------------------------------------------------------------#

$data << EOD
Title "clojure map" "proto map" "Java POJO"
"5" 110796318 45148103 55909694 
"10" 44215955 27451380 39729564  
"20" 41591789 15233909 19571473  
EOD

set output "write1_tp.png"
set ylabel "Throughput (ops/sec)"
set xlabel "Schema size"

plot "$data" \
    using 2:xtic(1) title col, '' using 3:xtic(1) title col, '' using 4:xtic(1) title col


#--------------------------------------------------------------------#


$data << EOD
Title "proto-map with p->" "Java" "proto-map with hinted p->"
"2" 19422313 40146145 30717038 
"5" 12276606 35632854 30266076    
"10" 7229973 34506822 29101215 
EOD

set output "write10_hinted_tp.png"
set title "10 keys schema"
set ylabel "Throughput (ops/sec)"
set xlabel "Number of assocs"

plot "$data" \
    using 2:xtic(1) title col, '' using 3:xtic(1) title col, '' using 4:xtic(1) title col


#--------------------------------------------------------------------#

$data << EOD
Title "proto-map with p->" "Java" "proto-map with hinted p->"
"2"  14068479 19650164 17758627 
"5"  10014848 17859690 16975859 
"10" 6895203 16628973 15634285 
EOD

set output "write20_hinted_tp.png"
set title "20 keys schema"
set ylabel "Throughput (ops/sec)"
set xlabel "Number of assocs"

plot "$data" \
    using 2:xtic(1) title col, '' using 3:xtic(1) title col, '' using 4:xtic(1) title col

#--------------------------------------------------------------------#

$data << EOD
Title "clojure map" "proto map" "proto-map with p->" "Java"
"2" 23126342 13594896 19422313 40146145 
"5" 11570077 4698214 12276606 35632854    
"10" 4886196 2262237 7229973 34506822  
EOD

set output "write10_tp.png"
set title "10 keys schema"
set ylabel "Throughput (ops/sec)"
set xlabel "Number of assocs"

plot "$data" \
    using 2:xtic(1) title col, '' using 3:xtic(1) title col, '' using 4:xtic(1) title col, '' using 5:xtic(1) title col


#--------------------------------------------------------------------#


$data << EOD
Title "clojure map" "proto map" "proto-map with p->" "Java"
"2" 23097030 8109611 14068479 19650164 
"5" 10855545 3209984 10014848 17859690   
"10" 4769433 1634291 6895203 16628973 
EOD

set output "write20_tp.png"
set title "20 keys schema"
set ylabel "Throughput (ops/sec)"
set xlabel "Number of assocs"

plot "$data" \
    using 2:xtic(1) title col, '' using 3:xtic(1) title col, '' using 4:xtic(1) title col, '' using 5:xtic(1) title col

