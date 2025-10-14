import moment from 'moment';
import {hierarchy, pack, select} from 'd3';

export function phpUnitBubbles(options) {

    let width = options.width ?? 600;
    let height = options.height ?? 450;
    let padding = options.padding ?? 1;

    let className = options.className ?? 'bubbles';

    let sort = function (a, b) {
        return Math.random() < 0.5;
    };

    let onMouseMove = function (event) {
        select('#tooltip')
            .style('left', (event.pageX - 190) + 'px')
            .style('top', (event.pageY + 5) + 'px');
    }

    let onMouseOver = function (e, rawData) {
        // Update tooltip content
        let data = rawData.data;
        let content = '<h3>' + data.name + '</h3>';

        if (data.error) {
            content += '<div class="error">';
            content += '    <h5>' + data.error.type + '</h5>';
            content += '    <pre>' + data.error.message + '</pre>';
            content += '</div>';
        } else if (data.failure) {
            content += '<div class="failure">';
            content += '    <h5>' + data.failure.type + '</h5>';
            content += '    <pre>' + data.failure.message + '</pre>';
            content += '</div>';
        } else if (data.skipped) {
            content += '<div class="skipped">';
            content += '    <h5>' + data.skipped.type + '</h5>';
            content += '</div>';
        }
        content += '<span class="time"><strong>Time:</strong> '
            + convertDuration(data.value * 1000) + '</span>';

        select('#tooltip').html(content);

        // Display tooltip
        select('#tooltip')
            .classed('errored', false)
            .classed('failed', false)
            .classed('success', false)
            .classed(getNodeClass(rawData), true)
            .transition()
            .duration(400)
            .style('opacity', 1);
    }

    let onMouseDown = function (e, data) {

        let objDiv = document.getElementById('error-stack-wrapper');
        let errorStack = select('#error-stack-wrapper');
        errorStack.classed('hidden', false);
        let tooltip = select('#tooltip').clone(this);
        tooltip.style('left', 0)
        tooltip.style('top', 0).attr('id', null)
        //console.log(objDiv.scrollHeight);

        tooltip.style('position', 'relative');
        //tooltip = tooltip.select(tooltip.parentNode);
        select('#error-stack').append('div').attr('class', tooltip.attr('class'))
            .classed('w-sm', false)
            .classed('max-w-lg', false)
            .classed('min-w-full', true)
            .classed('order-first', true)
            .classed('relative', true).html(tooltip.html());
        tooltip.remove();
        //console.log(objDiv.scrollHeight);
        objDiv.scrollTop = objDiv.scrollHeight;

    }

    let onMouseOut = function () {
        select('#tooltip').style('opacity', 0);
    }

    function hierarchicData(data) {

        let children = [];

        for (let i = 0, c = data.length; i < c; i++) {
            let datum = data[i];
            if (datum.time < 0.005) {
                continue;
            }

            children.push({
                name: datum.name,
                value: datum.time,
                type: datum.type,
                skipped: datum.skipped,
                error: datum.error,
                failure: datum.failure
            });
        }

        return {children: children};
    }

    function convertDuration(milliseconds) {

        if (milliseconds < 1000) {
            return Math.round(milliseconds) + 'ms';
        }

        let duration = moment.duration(milliseconds);
        let seconds = duration.get('seconds');

        if (seconds < 10) {
            seconds = '0' + seconds;
        }

        return duration.get('minutes') + ':' + seconds;
    }

    function getNodeClass(d) {
        if (d.data.skipped) {
            return 'skipped ';
        }

        if (d.data.error) {
            return 'error';
        }

        if (d.data.failure) {
            return 'failed ';
        }

        return 'success';
    }

    function getTooltipClass(d) {
        if (d.data.error) {
            return 'error';
        }

        if (d.data.failure) {
            return 'failed';
        }

        return 'success';
    }

    function chart(selection) {
        selection.each(function (data) {
            let root = hierarchy(hierarchicData(data))
                .sum(d => d.value)
                .sort(sort);

            let packLayout = pack()
                .size([width, height])
                .padding(padding);

            let nodes = packLayout(root).leaves();

            select(this).select('svg').remove();

            let svg = select(this)
                .append('svg')
                .attr('viewBox', '0 0 ' + width + ' ' + height)
                .attr('width', width)
                .attr('height', height)
                .attr('class', className);

            let node = svg
                .selectAll('.bubble')
                .data(nodes);

            let nodeEnter = node.enter()
                .append('g')
                .attr('class', 'bubbles')
                .attr('transform', function (d) {
                    return 'translate(' + d.x + ',' + d.y + ')';
                });

            nodeEnter
                .append('circle')
                .attr('r', function (d) {
                    return d.r;
                })
                .attr('class', getNodeClass);

            // nodeEnter
            //     .on('mouseover', onMouseOver)
            //     .on('mousemove', onMouseMove)
            //     .on('click', onMouseDown)
            //     .on('mouseout', onMouseOut);
        });
    }

    chart.width = function (value) {
        if (!arguments.length) return width;
        width = value;

        return chart;
    };

    chart.height = function (value) {
        if (!arguments.length) return height;
        height = value;

        return chart;
    };

    chart.padding = function (value) {
        if (!arguments.length) return padding;
        padding = value;

        return chart;
    };

    chart.className = function (value) {
        if (!arguments.length) return className;
        className = value;

        return chart;
    };

    chart.sort = function (value) {
        if (!arguments.length) return sort;
        sort = value;

        return chart;
    };

    // chart.onMouseOver = function (value) {
    //     if (!arguments.length) return onMouseOver;
    //     onMouseOver = value;
    //
    //     return chart;
    // };
    //
    // chart.onMouseMove = function (value) {
    //     if (!arguments.length) return onMouseMove;
    //     onMouseMove = value;
    //
    //     return chart;
    // };
    //
    // chart.onMouseDown = function (value) {
    //     if (!arguments.length) return onMouseDown;
    //     onMouseDown = value;
    //
    //     return chart;
    // };
    //
    // chart.onMouseOut = function (value) {
    //     if (!arguments.length) return onMouseOut;
    //     onMouseOut = value;
    //
    //     return chart;
    // };

    return chart;
}