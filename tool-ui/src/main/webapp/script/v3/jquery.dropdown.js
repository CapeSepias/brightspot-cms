define([ 'string', 'bsp-utils' ], function (S, bsp_utils) {
    
/** Better drop-down list than standard SELECT. */
(function($, win, undef) {

  var $win = $(win),
      doc = win.document,
      $doc = $(doc),
      $openOriginal,
      $openList;

  var dropdownIdIndex = 0;

  $.plugin2('dropDown', {
    '_defaultOptions': {
      'classPrefix': 'dropDown-'
    },

    'className': function(name) {
      return this.option('classPrefix') + name;
    },

    _initVisible: function ($original) {
      var plugin = this,
          isFixedPosition = $original.isFixedPosition(),
          isMultiple = $original.is('[multiple]'),
          isSearchable = $original.is('[data-searchable="true"]'),
          placeholder = $original.attr('placeholder'),
          dynamicPlaceholderText = $original.attr('data-dynamic-placeholder'),
          dynamicPlaceholderHtml = dynamicPlaceholderText && '<span data-dynamic-text=' + dynamicPlaceholderText + '>',
          $input,
          $label,
          $search,
          containerCss,
          $markerContainer,
          $marker,
          $listContainer,
          $list,
          addItem;

      var dropdownId = dropdownIdIndex;
      ++ dropdownIdIndex;

      if (!isMultiple &&
          $original.find('option:selected').length === 0) {
        $original.find('option:first').prop('selected', true);
      }

      $original.bind('input-disable', function(event, disable) {
        $input.toggleClass('state-disabled', disable);
      });
      
      var width = $original.outerWidth();
      
      if (isMultiple) {
        width = width * 2;
      }

      $input = $('<div/>', {
        'class': plugin.className('input'),
        'data-dropdown-id': dropdownId,
        'css': {
          'margin-bottom': $original.css('margin-bottom'),
          'margin-left': $original.css('margin-left'),
          'margin-right': $original.css('margin-right'),
          'margin-top': $original.css('margin-top'),
          'max-width': '100%',
          'position': 'relative',
          'width': width
        }
      });

      $input.click(function() {
        if ($label.is(':visible')) {
          $label.click();

        } else {
          if ($input.hasClass(plugin.className('list-open'))) {
            $list.trigger('dropDown-close');

          } else {
            $search.click();
          }
        }

        return false;
      });

      $label = $('<a/>', {
        'class': plugin.className('label'),
        'href': '#',
        'click': function() {
          if (!$input.is('.state-disabled')) {
            if ($openList && $openList[0] === $list[0]) {
              $list.trigger('dropDown-close');

            } else {
              $list.trigger('dropDown-open');
            }
          }

          return false;
        }
      });

      function resize() {
        var inputOffset = $input.offset();
        var inputWidth = $input.outerWidth(true);
        var winScrollTop = $win.scrollTop();
        var winHeight = $win.height();

        if (inputWidth > $listContainer.outerWidth(true)) {
          $listContainer.css('min-width', inputWidth + 20);
        }

        if (inputOffset.top - winScrollTop < winHeight * 0.6) {
          var inputHeight = $input.outerHeight();
          var markerTop = inputOffset.top + inputHeight;

          if (isFixedPosition) {
            markerTop -= $win.scrollTop();
          }

          $list.css('max-height', winScrollTop + winHeight - inputOffset.top - inputHeight);

          $input.add($listContainer).add($markerContainer).removeClass('dropDown-input-bottom');
          $listContainer.add($markerContainer).css({
            'bottom': '',
            'left': inputOffset.left,
            'top': markerTop
          });

        } else {
          var markerBottom = winHeight - inputOffset.top;

          if (isFixedPosition) {
            markerBottom += $win.scrollTop();
          }

          $list.css('max-height', inputOffset.top - winScrollTop);

          $input.add($listContainer).add($markerContainer).addClass('dropDown-input-bottom');
          $listContainer.add($markerContainer).css({
            'bottom': markerBottom,
            'left': inputOffset.left,
            'top': ''
          });
        }

        $markerContainer.css('width', $input.outerWidth());
      }

      $label.bind('dropDown-update', function() {
        var selected = $original.find('option').filter(':selected');
        var newLabel;
        if (selected) {
          var $textAndHtml = $(document.createElement('div'));
          var lengthOfSelected = selected.length;
          for (var i = 0; i < lengthOfSelected; ++i) {
            var $option = $(selected[i]);
            if ($option.attr('data-drop-down-html')) {
              $textAndHtml.append($option.attr('data-drop-down-html'));
            } else {
              $textAndHtml.append(document.createTextNode($option.text()));
            }
            if (i < lengthOfSelected - 1) {
              $textAndHtml.append(', ');
            }
          }
          newLabel = $textAndHtml.html();
        }
        if (newLabel || dynamicPlaceholderHtml || !placeholder) {
          $label.html(newLabel || dynamicPlaceholderHtml || '&nbsp;');
        } else {
          $label.text(placeholder);
        }

        $label.toggleClass('state-placeholder', !newLabel);

        resize();
      });

      containerCss = {
        'display': 'none',
        'position': isFixedPosition ? 'fixed' : 'absolute',
        'z-index': $original.zIndex() + 1000000
      };

      $markerContainer = $('<div/>', {
        'css': containerCss
      });

      $marker = $('<div/>', {
        'class': plugin.className('marker')
      });

      $listContainer = $('<div/>', {
        'class': plugin.className('container'),
        'data-dropdown-id': dropdownId,
        'data-original-class': $original.attr('class'),
        'css': containerCss
      });

      $list = $('<div/>', {
        'class': plugin.className('list')
      });

      $list.bind('dropDown-open', function() {
        resize();

        $input.addClass(plugin.className('list-open'));

        $list.find('.' + plugin.className('listItem')).removeClass('state-hover');
        $list.find('.' + plugin.className('listItem') + (isMultiple ? ':first' : ':has(:checked)')).addClass('state-hover');

        if ($openList) {
          $openList.trigger('dropDown-close');
        }

        $openOriginal = $original;
        $openList = $list;
        $markerContainer.show();
        $listContainer.show();
      });

      $list.bind('dropDown-close', function() {
        $input.removeClass(plugin.className('list-open'));

        $openOriginal = null;
        $openList = null;
        $markerContainer.hide();
        $listContainer.hide();

        if (isMultiple) {
          $original.change();
        }
      });

      $list.bind('dropDown-hover', function(event, $item) {
        $list.find('.' + plugin.className('listItem')).removeClass('state-hover');

        if ($item) {
          $item.addClass('state-hover');
        }
      });

      $list.bind('mousewheel', function(event, delta, deltaX, deltaY) {
        var $list = $(this),
            maxScrollTop = $.data(this, 'dropDown-maxScrollTop');

        if (typeof maxScrollTop === 'undefined') {
          maxScrollTop = $list.prop('scrollHeight') - $list.innerHeight();
          $.data(this, 'dropDown-maxScrollTop', maxScrollTop);
        }

        if ((deltaY > 0 && $list.scrollTop() === 0) ||
            (deltaY < 0 && $list.scrollTop() >= maxScrollTop)) {
          event.preventDefault();
        }
      });

      // Detect clicks within the window to toggle the list properly.
      $doc.mousedown(function(event) {
        if ($listContainer.is(':visible') &&
            !$.contains($listContainer[0], event.target) &&
            $input[0] !== event.target &&
            !$.contains($input[0], event.target)) {
          $list.trigger('dropDown-close');
        }
      });

      // Create the list based on the options in the original input.
      addItem = function($option) {
        var $item,
            $check;

        $item = $('<div/>', {
          'class': plugin.className('listItem')}
        );

        if ($option.attr("data-drop-down-html") || !$option.text()) {
          $item.html($option.attr("data-drop-down-html") || '&nbsp;');
        } else {
          $item.text($option.text());
        }

        $check = $('<input/>', {
          'type': isMultiple ? 'checkbox' : 'radio'
        });

        if ($option.is(':selected')) {
          $check.prop('checked', true);
          $item.addClass(plugin.className('listItem-selected'));
        }

        $item.mouseenter(function() {
          $list.trigger('dropDown-hover', [ $item ]);
        });

        $item.mouseleave(function() {
          $list.trigger('dropDown-hover');
        });

        $item.click(isMultiple ? function() {
          if ($option.is(':selected')) {
            $option.prop('selected', false);
            $item.find(':checkbox').prop('checked', false);
            $item.removeClass(plugin.className('listItem-selected'));

          } else {
            $option.prop('selected', true);
            $item.find(':checkbox').prop('checked', true);
            $item.addClass(plugin.className('listItem-selected'));
          }

          $label.trigger('dropDown-update');

          return false;

        } : function() {
          if (!$option.is(':selected')) {
            $original.find('option').prop('selected', false);
            $list.find(':radio').prop('checked', false);
            $list.find('.' + plugin.className('listItem')).removeClass(plugin.className('listItem-selected'));

            $option.prop('selected', true);
            $check.prop('checked', true);
            $item.addClass(plugin.className('listItem-selected'));

            $label.trigger('dropDown-update');
            $original.change();
          }

          $list.trigger('dropDown-close');

          return false;
        });

        $item.prepend(' ');
        $item.prepend($check);
        $list.append($item);
      };

      $original.find('> optgroup, > option').each(function() {
        var $child = $(this);

        if ($child.is('option')) {
          addItem($child);

        } else {
          $list.append($('<div/>', {
            'class': plugin.className('listGroupLabel'),
            'text': $child.attr('label')
          }));

          $child.find('> option').each(function() {
            addItem($(this));
          });
        }
      });

      // Replace input with the custom control.
      $label.trigger('dropDown-update');
      $input.append($label);
      $original.before($input);
      $original.hide();

      $listContainer.append($list);
      $(doc.body).append($listContainer);
      $listContainer.css('min-width', $listContainer.outerWidth());

      $markerContainer.append($marker);
      $(doc.body).append($markerContainer);

      if (isSearchable) {
        $search = $('<input/>', {
          'class': plugin.className('search'),
          'type': 'text'
        });

        $search.bind('input', function() {
          var re = new RegExp(S($search.val().replace(/\s/, '').split('').join('(?:.*\\W)?')).latinise().s, 'i'),
              $first;

          $list.find('.' + plugin.className('listItem')).each(function() {
            var $item = $(this);

            if (re.test(S($item.text()).latinise().s)) {
              $item.show();

              if (!$first) {
                $first = $item;
              }

            } else {
              $item.hide();
            }
          });

          $list.trigger('dropDown-hover', [ $first ]);
        });

        $search.click(function() {
          return false;
        });

        $list.bind('dropDown-open', function() {
          $label.hide();
          $list.find('.' + plugin.className('listItem')).show();

          $search.val($label.text());
          $search.show();
          $search.focus();
          $search.select();
        });

        $list.bind('dropDown-close', function() {
          $label.show();
          $search.hide();
        });

        $search.hide();
        $input.append($search);
      }
    },

    '_create': function(original) {
      var plugin = this;
      var $original = $(original);
      var init = function () {
        plugin._initVisible($original);
      };

      $win.resize(function () {
        if ($original.is(':visible')) {
          $win.unbind('resize', init);
          init();
        }
      })
    }
  });

  $doc.keydown(function(event) {
    var which,
        isUp,
        LIST_ITEM_CLASS,
        $hover,
        hoverTop,
        hoverHeight,
        delta;

    if ($openList) {
      which = event.which;
      isUp = which === 38;

      if (!(event.altKey || event.ctrlKey || event.metaKey || event.shiftKey)) {
        LIST_ITEM_CLASS = $openOriginal.dropDown('className', 'listItem');

        if (isUp || which === 40) {
          $hover = $openList.find('.state-hover:visible.' + LIST_ITEM_CLASS).eq(0);

          if ($hover.length === 0) {
            $hover = $openList.find(':visible.' + LIST_ITEM_CLASS).eq(isUp ? -1 : 0);

          } else {
            $hover = $hover[isUp ? 'prevAll' : 'nextAll'](':visible.' + LIST_ITEM_CLASS).eq(0);
          }

          if ($hover.length > 0) {
            $openList.trigger('dropDown-hover', [ $hover ]);

            hoverTop = $hover.position().top;
            hoverHeight = $hover.outerHeight();

            if (isUp) {
              if (hoverTop < 0) {
                $openList.scrollTop($openList.scrollTop() + hoverTop);
              }

            } else {
              delta = hoverTop + hoverHeight - $openList.height();

              if (delta > 0) {
                $openList.scrollTop($openList.scrollTop() + delta);
              }
            }
          }

          return false;

        } else if (which === 13) {
          $openList.find('.state-hover.' + LIST_ITEM_CLASS).click();

          return false;

        } else if (which === 27) {
          $openList.trigger('dropDown-close');

          return false;
        }
      }
    }

    return true;
  });
  
}(jQuery, window));

    // Return an empty object just for the benefit of the AMD module.
    // Not expected to be used since we just set up a jquery plugin.
    return {};
    
}); // define()
